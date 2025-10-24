package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import ru.tinkoff.kora.ksp.common.TestUtils.asCompilationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.*

class KotlinCompilation {
    val processors = arrayListOf<SymbolProcessorProvider>()
    val srcFiles = arrayListOf<Path>()
    val javaSrcFiles = arrayListOf<Path>()
    val processorsOptions = mutableMapOf<String, String>()
    val classpathEntries = mutableListOf<Path>()
    var outputDir = Path.of("build/in-test-generated-ksp/sources")
    val classpath = mutableListOf<File>()
    lateinit var classOutputDir: Path

    @OptIn(ExperimentalAtomicApi::class)
    val baseDir = KotlinCompilation.baseDir.resolve("test-" + UUID.randomUUID()).toAbsolutePath()

    @OptIn(ExperimentalPathApi::class, ExperimentalAtomicApi::class)
    constructor() {
        baseDir.absolute().deleteRecursively()
        baseDir.absolute().createDirectories()
    }

    fun withProcessor(p: SymbolProcessorProvider) = apply { processors.add(p) }
    fun withProcessors(p: List<SymbolProcessorProvider>) = apply { processors.addAll(p) }
    fun withSrc(p: Path) = apply { srcFiles.add(p) }
    fun withSrc(p: List<Path>) = apply { srcFiles.addAll(p) }
    fun withSrc(p: String) = apply { srcFiles.add(Path.of(p)) }
    fun withGeneratedSourcesDir(kotlinSourcesDir: Path) = apply { outputDir = kotlinSourcesDir }
    fun withJavaSrcs(javaFiles: List<Path>) = apply { javaSrcFiles.addAll(javaFiles) }

    fun withFullClasspath() = apply {
        classpath.clear()
        classpath.addAll(TestUtils.classpath.map { File(it) })
    }

    fun withPartialClasspath() = apply {
        classpath.clear()
        Path.of("").toAbsolutePath().resolve("build", "libs").toAbsolutePath().walk().map { it.toFile() }.forEach { classpath.add(it) }
        classpath.add(Path.of("").toAbsolutePath().resolve("build", "classes", "kotlin", "test").toAbsolutePath().toFile())
        classpath.add(Path.of("").toAbsolutePath().resolve("build", "classes", "java", "test").toAbsolutePath().toFile())
        withClasspathJar("kotlin-stdlib")
            .withClasspathJar("micrometer-core")
            .withClasspathJar("opentelemetry-context")
            .withClasspathJar("opentelemetry-api")
            .withClasspathJar("jakarta.annotation-api")
            .withClasspathJar("kotlinx-coroutines-core-jvm")
            .withClasspathJar("kotlinx-coroutines-jdk8")
            .withClasspathJar("slf4j-api")
            .withClasspathJar("mockito-core")
            .withClasspathRegex(".*/build/libs/.*jar")
    }

    fun withClasspathRegex(regex: String) = apply {
        val p = regex.toRegex()
        TestUtils.classpath
            .filter { p.matches(it) }
            .forEach { classpath.add(File(it)) }
    }

    fun withClasspathJar(jarName: String) = apply {
        val p = (".*/$jarName.*jar$").toRegex()
        TestUtils.classpath
            .filter { p.matches(it) }
            .forEach { classpath.add(File(it)) }
    }

    fun compile(): ClassLoader {
        val start = System.currentTimeMillis()
        val finalGeneratedSourcesPath = outputDir.toAbsolutePath()
        finalGeneratedSourcesPath.createDirectories()
        val kspOut = baseDir.resolve("kotlinOutputDir")

        val generatedFiles = symbolProcessFiles()
        val kspTime = System.currentTimeMillis() - start
        println("ksp took ${kspTime}ms")
        for (generatedFile in generatedFiles) {
            val relativePath = kspOut.relativize(generatedFile)
            val finalPath = finalGeneratedSourcesPath.resolve(relativePath)
            finalPath.parent.createDirectories()
            Files.copy(generatedFile, finalPath, StandardCopyOption.REPLACE_EXISTING)
        }

        val filesToCompile = srcFiles + generatedFiles
        val cl = kotlinCompileFiles(filesToCompile)
        println("kotlinc took ${System.currentTimeMillis() - start - kspTime}ms")
        return cl
    }


    @OptIn(ExperimentalPathApi::class)
    fun symbolProcessFiles(): List<Path> {
        val sw = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(
            PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.PLAIN_FULL_PATHS, true
        )
        val kspConfig = com.google.devtools.ksp.processing.KSPJvmConfig.Builder().apply {
            moduleName = "main"
            jvmTarget = "24"
            languageVersion = "2.2"
            apiVersion = "2.2"
            processorOptions = processorsOptions
            jdkHome = File(System.getProperty("java.home"))
            mapAnnotationArgumentsInJava = true
            libraries = classpath + classpathEntries.map { it.toFile() }
            sourceRoots = srcFiles.map { it.toFile() }
            javaSourceRoots = javaSrcFiles.map { it.toFile() }
            kotlinOutputDir = baseDir.resolve("kotlinOutputDir").toFile()
            javaOutputDir = baseDir.resolve("javaOutputDir").toFile()
            projectBaseDir = baseDir.toFile()
            outputBaseDir = baseDir.resolve("outputBaseDir").toFile()
            cachesDir = baseDir.resolve("cachesDir").toFile()
            classOutputDir = baseDir.resolve("classOutputDir").toFile()
            resourceOutputDir = baseDir.resolve("resourceOutputDir").toFile()
        }.build()
        val l = MessageCollectorBasedKSPLogger(collector, collector, false)
        val exitCode = KotlinSymbolProcessing(kspConfig, processors, l).execute()
        l.reportAll()
        val messages = sw.toString().split("\n")
        if (exitCode != KotlinSymbolProcessing.ExitCode.OK || messages.any { it.startsWith("error: [ksp]") }) {
            throw messages.asCompilationException()
        }

        return kspConfig.kotlinOutputDir.walk().filter { it.isFile }.map { it.toPath() }.toList()
    }

    fun kotlinCompileFiles(files: List<Path>): ClassLoader {
        classOutputDir = baseDir.resolve("classes")

        val co = K2JVMCompiler()
        val k2JvmArgs = co.createArguments()
        k2JvmArgs.noReflect = true
        k2JvmArgs.noStdlib = true
        k2JvmArgs.noJdk = false
        k2JvmArgs.includeRuntime = false
        k2JvmArgs.script = false
        k2JvmArgs.disableStandardScript = true
        k2JvmArgs.help = false
        k2JvmArgs.compileJava = javaSrcFiles.isNotEmpty()
        k2JvmArgs.allowNoSourceFiles = true
        k2JvmArgs.expression = null
        k2JvmArgs.destination = classOutputDir.toString()
        k2JvmArgs.jvmTarget = "24"
        k2JvmArgs.jvmDefault = "all"
        k2JvmArgs.jdkHome = System.getProperty("java.home")
        k2JvmArgs.freeArgs = files.map { it.toString() }
        k2JvmArgs.classpath = (classpath + classpathEntries.map { it.toString() }).joinToString(File.pathSeparator)
        k2JvmArgs.javaSourceRoots = javaSrcFiles.map { it.toString() }.toTypedArray()


        val sw = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(
            PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
        )

        val code = co.exec(collector, Services.EMPTY, k2JvmArgs)
        if (code != ExitCode.OK) {
            throw sw.toString().split("\n").asCompilationException()
        }
        if (collector.hasErrors()) {
            throw sw.toString().split("\n").asCompilationException()
        }
        return URLClassLoader("test-cl", arrayOf(URL("file://$classOutputDir/")), Thread.currentThread().contextClassLoader)
    }


    @OptIn(ExperimentalAtomicApi::class)
    companion object {
        val baseDir = Path.of("build/ksp-tests")
    }
}

