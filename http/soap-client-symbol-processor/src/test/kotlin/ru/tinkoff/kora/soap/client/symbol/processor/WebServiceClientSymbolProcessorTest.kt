package ru.tinkoff.kora.soap.client.symbol.processor

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.annotation.processor.common.TestUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors

class WebServiceClientSymbolProcessorTest {

    @Test
    fun testGenerate() {
        compileKotlin("build/generated/wsdl-jakarta-simple-service/")
        compileKotlin("build/generated/wsdl-javax-simple-service/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-multipart-response/")
        compileKotlin("build/generated/wsdl-javax-service-with-multipart-response/")
        compileKotlin("build/generated/wsdl-jakarta-service-with-rpc/")
        compileKotlin("build/generated/wsdl-javax-service-with-rpc/")
    }

    private fun compileKotlin(targetDir: String) {
        val k2JvmArgs = K2JVMCompilerArguments()
        val kotlinOutPath = Path.of("build/in-test-generated-ksp/ksp/sources/kotlin").toAbsolutePath().toString()
        k2JvmArgs.noReflect = true
        k2JvmArgs.noStdlib = true
        k2JvmArgs.noJdk = false
        k2JvmArgs.includeRuntime = false
        k2JvmArgs.script = false
        k2JvmArgs.disableStandardScript = true
        k2JvmArgs.help = false
        k2JvmArgs.expression = null
        k2JvmArgs.destination = "$kotlinOutPath/kotlin-classes"
        k2JvmArgs.jvmTarget = "17"
        k2JvmArgs.jvmDefault = "all"
        k2JvmArgs.compileJava = true
        k2JvmArgs.verbose = true
        k2JvmArgs.javaSourceRoots = arrayOf(Paths.get(targetDir).toAbsolutePath().toString())
        k2JvmArgs.freeArgs = listOf("build/tmp/empty.kt")
        k2JvmArgs.classpath = java.lang.String.join(File.pathSeparator, TestUtils.classpath)
        val pluginClassPath: Array<String> = TestUtils.classpath.stream()
            .filter { it.contains("symbol-processing") }
            .toArray { Array(it) { "" } }
        val processors: String = TestUtils.classpath.stream()
            .filter { it.contains("symbol-processor") && (it.endsWith(".jar") || it.endsWith("main")) }
            .collect(Collectors.joining(File.pathSeparator))
        k2JvmArgs.pluginClasspaths = pluginClassPath
        val ksp = "plugin:com.google.devtools.ksp.symbol-processing:"
        k2JvmArgs.pluginOptions = arrayOf(
            ksp + "kotlinOutputDir=" + kotlinOutPath,
            ksp + "kspOutputDir=" + kotlinOutPath,
            ksp + "classOutputDir=" + kotlinOutPath,
            ksp + "javaOutputDir=" + kotlinOutPath,
            ksp + "projectBaseDir=" + Path.of(".").toAbsolutePath(),
            ksp + "resourceOutputDir=" + kotlinOutPath,
            ksp + "cachesDir=" + kotlinOutPath,
            ksp + "apclasspath=" + processors
        )
        Files.writeString(
            Path.of("build/tmp/empty.kt"),
            "fun test() { }",
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        )
        val sw = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(
            PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
        )
        val co = K2JVMCompiler()
        val code = co.exec(collector, Services.EMPTY, k2JvmArgs)
        if (code != org.jetbrains.kotlin.cli.common.ExitCode.OK) {
            throw RuntimeException(sw.toString(StandardCharsets.UTF_8))
        }
        println(sw.toString(StandardCharsets.UTF_8))
    }

}
