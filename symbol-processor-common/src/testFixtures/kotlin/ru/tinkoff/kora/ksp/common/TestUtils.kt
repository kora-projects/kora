package ru.tinkoff.kora.ksp.common

//import com.tschuchort.compiletesting.KotlinCompilation
//import com.tschuchort.compiletesting.SourceFile
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest.ProcessorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors
import kotlin.io.path.*
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Deprecated(replaceWith = ReplaceWith("ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest"), message = "")
object TestUtils {

    fun testKoraExtension(targetClasses: Array<KType>, vararg requiredDependencies: KType): ClassLoader? {
        var template: String = """
        package test;
                    
        @ru.tinkoff.kora.common.KoraApp
        public interface TestApp {
            @ru.tinkoff.kora.common.annotation.Root
            fun someLifecycle({targets}) = Any()
    """.trimIndent()
        for (i in requiredDependencies.indices) {
            template += """  fun component$i() : ${requiredDependencies[i]} { return null!!; }
"""
        }
        template += "\n}"
        val sb = StringBuilder()
        for (i in targetClasses.indices) {
            if (i > 0) {
                sb.append(",\n  ")
            }
            sb.append("param").append(i).append(": ").append(targetClasses[i].toString())
        }
        val targets = sb.toString()
        val content = template.replace("\\{targets}".toRegex(), targets)
        val path = Path.of("build/in-test-generated/extension-test-dir/test/TestApp.kt")
        Files.deleteIfExists(path)
        Files.createDirectories(path.parent)
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
        val koraAppProcessor = Class.forName("ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider").getConstructor().newInstance() as SymbolProcessorProvider
        return symbolProcessFiles(listOf(path.toString()))
    }
}

fun symbolProcess(targetClasses: List<KClass<*>>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcess(targetClasses)
}

fun symbolProcess(targetClass: KClass<*>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcess(listOf(targetClass))
}

fun symbolProcessJava(targetClass: Class<*>, vararg annotationProcessorProviders: SymbolProcessorProvider): ClassLoader {
    return symbolProcessJava(listOf(targetClass))
}

fun symbolProcess(targetClass: KClass<*>, processorOptions: List<ProcessorOptions>): ClassLoader {
    return symbolProcess(listOf(targetClass), processorOptions)
}

fun symbolProcessJava(targetClasses: List<Class<*>>): ClassLoader {
    val srcFilesPath = targetClasses.map { targetClass ->
        Path.of("src/test/java/" + targetClass.canonicalName.replace(".", "/") + ".java").toAbsolutePath().toString()
    }
    return symbolProcessFiles(srcFilesPath)
}

fun symbolProcess(targetClasses: List<KClass<*>>): ClassLoader {
    val srcFilesPath = targetClasses.map { targetClass ->
        "src/test/kotlin/" + targetClass.qualifiedName!!.replace(".", "/") + ".kt"
    }
    return symbolProcessFiles(srcFilesPath)
}

fun symbolProcess(targetClasses: List<KClass<*>>, processorOptions: List<ProcessorOptions>): ClassLoader {
    val srcFilesPath = targetClasses.map { targetClass ->
        "src/test/kotlin/" + targetClass.qualifiedName!!.replace(".", "/") + ".kt"
    }
    return symbolProcessFiles(srcFilesPath, processorOptions)
}

@OptIn(ExperimentalPathApi::class)
fun symbolProcessFiles(srcFiles: List<String>): ClassLoader {
    return symbolProcessFiles(srcFiles, listOf())
}

@OptIn(ExperimentalPathApi::class)
fun symbolProcessFiles(srcFiles: List<String>, processorOptions: List<ProcessorOptions>): ClassLoader {
    val k2JvmArgs = K2JVMCompilerArguments()
    val kotlinOutPath = Path.of("build/in-test-generated-ksp").toAbsolutePath()
    val inTestGeneratedDestination = kotlinOutPath.resolveSibling("in-test-generated-destination")
    val kotlinOutputDir = kotlinOutPath.resolveSibling("in-test-generated-kotlinOutputDir")
    inTestGeneratedDestination.deleteRecursively()
    kotlinOutputDir.deleteRecursively()
    kotlinOutputDir.createDirectories()

    k2JvmArgs.noReflect = true
    k2JvmArgs.noStdlib = true
    k2JvmArgs.noJdk = false
    k2JvmArgs.includeRuntime = false
    k2JvmArgs.script = false
    k2JvmArgs.disableStandardScript = true
    k2JvmArgs.help = false
    k2JvmArgs.compileJava = true
    k2JvmArgs.allowNoSourceFiles = true
    k2JvmArgs.expression = null
    k2JvmArgs.destination = inTestGeneratedDestination.toString()
    k2JvmArgs.jvmTarget = "17"
    k2JvmArgs.jvmDefault = "all"
    k2JvmArgs.freeArgs = srcFiles
    k2JvmArgs.assertionsMode
    k2JvmArgs.classpath = AbstractSymbolProcessorTest.classpath.joinToString(File.pathSeparator);

    val pluginClassPath = AbstractSymbolProcessorTest.classpath.asSequence()
        .filter { it.contains("symbol-processing") }
        .toList()
        .toTypedArray()
    val processors = AbstractSymbolProcessorTest.classpath.stream()
        .filter { it.contains("symbol-processor") || it.contains("scheduling-ksp") }
        .collect(Collectors.joining(File.pathSeparator))
    k2JvmArgs.pluginClasspaths = pluginClassPath
    val ksp = "plugin:com.google.devtools.ksp.symbol-processing:"
    k2JvmArgs.pluginOptions = arrayOf(
        ksp + "kotlinOutputDir=" + kotlinOutputDir,
        ksp + "kspOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-kspOutputDir"),
        ksp + "classOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-classOutputDir"),
        ksp + "incremental=false",
        ksp + "javaOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-javaOutputDir"),
        ksp + "projectBaseDir=" + Path.of(".").toAbsolutePath(),
        ksp + "resourceOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-resourceOutputDir"),
        ksp + "cachesDir=" + kotlinOutPath.resolveSibling("in-test-generated-cachesDir"),
        ksp + "apclasspath=" + processors,
    )
    k2JvmArgs.pluginOptions = k2JvmArgs.pluginOptions!! + processorOptions.map { o -> ksp + "apoption=" + o.value }.toTypedArray()

    val sw = ByteArrayOutputStream()
    val collector = PrintingMessageCollector(
        PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
    )
    val co = K2JVMCompiler()
    var code = co.exec(collector, Services.EMPTY, k2JvmArgs)
    kotlinOutPath.resolve("sources").createDirectories()
    kotlinOutputDir.copyToRecursively(
        kotlinOutPath.resolve("sources"),
        followLinks = false,
        overwrite = true
    )
    var cl = Thread.currentThread().contextClassLoader
    if (code != ExitCode.OK) {
        throw CompilationErrorException(sw.toString().split("\n"))
    }
    if (collector.hasErrors()) {
        throw CompilationErrorException(sw.toString().split("\n"))
    }
    k2JvmArgs.pluginClasspaths = null
    k2JvmArgs.freeArgs = srcFiles + Files.walk(kotlinOutputDir)
        .filter { it.isRegularFile() }
        .map {
            kotlinOutPath.resolve("sources").resolve(it.relativeTo(kotlinOutputDir)).toAbsolutePath().toString()
        }
        .toList()
    code = co.exec(collector, Services.EMPTY, k2JvmArgs)
    if (code != ExitCode.OK) {
        throw CompilationErrorException(sw.toString().split("\n"))
    }
    if (collector.hasErrors()) {
        throw CompilationErrorException(sw.toString().split("\n"))
    }
    return URLClassLoader("test-cl", arrayOf(URL("file://$inTestGeneratedDestination/")), Thread.currentThread().contextClassLoader)
}

data class CompilationErrorException(val messages: List<String>) : Exception(messages.joinToString("\n"))
