package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractSymbolProcessorTest {
    protected lateinit var testInfo: TestInfo
    protected lateinit var compileResult: CompileResult

    @BeforeEach
    fun beforeEach(testInfo: TestInfo) {
        this.testInfo = testInfo
        val testClass: Class<*> = this.testInfo.getTestClass().get()
        val testMethod: Method = this.testInfo.getTestMethod().get()
        val sources = Paths.get(".", "build", "in-test-generated-ksp", "sources")
//        sources.toFile().deleteRecursively()
        val path = sources
            .resolve(testClass.getPackage().name.replace('.', '/'))
            .resolve("packageFor" + testClass.simpleName)
            .resolve(testMethod.name)
        path.toFile().deleteRecursively()
        Files.createDirectories(path)
    }

    @AfterEach
    fun afterEach() {
        if (this::compileResult.isInitialized) {
            compileResult.classLoader.close()
        }
        val oldRoot = Path.of(".", "build", "in-test-generated-ksp", "ksp", "sources", "kotlin")
        val newRoot = Path.of(".", "build", "in-test-generated-ksp", "sources")
        Files.walk(oldRoot).forEach { oldPath ->
            if (Files.isDirectory(oldPath)) {
                return@forEach
            }
            val newPath = newRoot.resolve(oldRoot.relativize(oldPath))
            Files.createDirectories(newPath.parent)
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    protected fun loadClass(className: String) = this.compileResult.loadClass(className)

    protected fun testPackage(): String {
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        return testClass.packageName + ".packageFor" + testClass.simpleName + "." + testMethod.name
    }

    protected open fun commonImports(): String {
        return """
            import ru.tinkoff.kora.common.annotation.*;
            import ru.tinkoff.kora.common.*;
            import javax.annotation.Nullable;
            
            """.trimIndent()
    }

    protected fun compile(processors: List<SymbolProcessorProvider>, @Language("kotlin") vararg sources: String): CompileResult {
        val testPackage = testPackage()
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        val commonImports = commonImports()
        val sourceList: List<SourceFile> =
            Arrays.stream(sources).map { s: String -> "package %s;\n%s\n/**\n* @see %s.%s \n*/\n".formatted(testPackage, commonImports, testClass.canonicalName, testMethod.name) + s }
                .map { s ->
                    val firstClass = s.indexOf("class ") to "class ".length
                    val firstInterface = s.indexOf("interface ") to "interface ".length
                    val classNameLocation = sequenceOf(firstClass, firstInterface)
                        .filter { it.first >= 0 }
                        .map { it.first + it.second }
                        .flatMap {
                            sequenceOf(
                                s.indexOf(" ", it + 1),
                                s.indexOf("(", it + 1),
                                s.indexOf("{", it + 1),
                                s.indexOf(":", it + 1),
                            )
                                .map { it1 -> it to it1 }
                        }
                        .filter { it.second >= 0 }
                        .minBy { it.second }
                    val className = s.substring(classNameLocation.first - 1, classNameLocation.second)
                    val fileName = "build/in-test-generated-ksp/sources/${testPackage.replace('.', '/')}/$className.kt"
                    Files.createDirectories(File(fileName).toPath().parent)
                    Files.deleteIfExists(Paths.get(fileName))
                    Files.writeString(Paths.get(fileName), s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
                    SourceFile.kotlin(fileName.replace("build/in-test-generated-ksp/sources/", ""), s)
                }
                .toList()
        return this.symbolProcessFiles(sourceList, processors)
    }

    data class CompileResult(val testPackage: String, val exitCode: KotlinCompilation.ExitCode, val classLoader: URLClassLoader, val messages: List<String>) {
        fun loadClass(className: String): Class<*> {
            return classLoader.loadClass("$testPackage.$className")!!
        }

        fun isFailed(): Boolean {
            return exitCode != KotlinCompilation.ExitCode.OK
        }

        fun compilationException(): Throwable {
            val errorMessages = mutableListOf<String>()
            val indexOfFirst = messages.indexOfFirst { it.startsWith("e: ") }
            if (indexOfFirst >= 0) {
                for (i in indexOfFirst until messages.size) {
                    val message = messages[i]
                    if (i == indexOfFirst + 1 && !message.startsWith("[")) break
                    if (i != indexOfFirst && message.endsWith("]")) {
                        errorMessages.add(message.replace("]", ""))
                        break
                    } else {
                        errorMessages.add(
                            if (i != indexOfFirst) {
                                message.replace("[", "")
                            } else {
                                message
                            }
                        )
                    }
                }
            }
            throw RuntimeException(errorMessages.joinToString("\n"))
        }

        fun assertSuccess() {
            if (isFailed()) {
                throw compilationException()
            }
        }

    }

    protected fun symbolProcessFiles(srcFiles: List<SourceFile>, annotationProcessorProviders: List<SymbolProcessorProvider>): CompileResult {
        val compilation = KotlinCompilation().apply {
            jvmDefault = "all"
            jvmTarget = "17"
            workingDir = Path.of("build/in-test-generated-ksp").toAbsolutePath().toFile()
            sources = srcFiles
            symbolProcessorProviders = annotationProcessorProviders
            inheritClassPath = true
            verbose = false
            reportPerformance = true
        }
        val result = compilation.compile()
        val messages = result.messages.split("\n")
        compileResult = CompileResult(testPackage(), result.exitCode, result.classLoader, messages)
        return compileResult
    }

    protected fun new(name: String, vararg args: Any?) = compileResult.loadClass(name).constructors[0].newInstance(*args)!!

    interface GeneratedObject<T> : () -> T

    protected fun newGenerated(name: String, vararg args: Any?) = object : GeneratedObject<Any> {
        override fun invoke() = compileResult.loadClass(name).constructors[0].newInstance(*args)!!
    }

}
