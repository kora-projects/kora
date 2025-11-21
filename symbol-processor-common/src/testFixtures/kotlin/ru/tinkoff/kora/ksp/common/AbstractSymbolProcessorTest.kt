package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Mono
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractSymbolProcessorTest {

    protected lateinit var testInfo: TestInfo
    protected lateinit var compileResult: TestUtils.ProcessingResult
    protected val compileOptions: MutableMap<String, String> = mutableMapOf()

    @BeforeEach
    fun beforeEach(testInfo: TestInfo) {
        this.testInfo = testInfo
        val testClass: Class<*> = this.testInfo.getTestClass().get()
        val testMethod: Method = this.testInfo.getTestMethod().get()
        val sources = Paths.get(".", "build", "in-test-generated-ksp", "sources")
        val path = sources
            .resolve(testClass.getPackage().name.replace('.', '/'))
            .resolve("packageFor" + testClass.simpleName)
            .resolve(testMethod.name)
        path.toFile().deleteRecursively()
        Files.createDirectories(path)
    }

    @AfterEach
    fun afterEach() {
        if (this::compileResult.isInitialized && this.compileResult is TestUtils.ProcessingResult.Success) {
            this.compileResult.let { cr ->
                if (cr is TestUtils.ProcessingResult.Success && cr.classLoader is AutoCloseable) {
                    cr.classLoader.close()
                }
            }
        }
    }

    protected fun loadClass(className: String): Class<*> = this.compileResult.assertSuccess().classLoader.loadClass(testPackage() + "." + className)

    protected fun testPackage(): String {
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        return testClass.packageName + ".packageFor" + testClass.simpleName + "." + testMethod.name
    }

    protected open fun commonImports(): String {
        return """
            import ru.tinkoff.kora.common.annotation.*;
            import ru.tinkoff.kora.common.*;
            import jakarta.annotation.Nullable;
            
            """.trimIndent()
    }

    protected fun compile0(processors: List<SymbolProcessorProvider>, @Language("kotlin") vararg sources: String): TestUtils.ProcessingResult {
        val testPackage = testPackage()
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        val commonImports = commonImports()
        val kc = KotlinCompilation()
            .withProcessors(processors)
            .apply { processorsOptions.putAll(compileOptions) }
        val sourceList = sequenceOf(*sources)
            .map { s: String -> "package $testPackage;\n$commonImports\n/**\n* @see ${testClass.canonicalName}.${testMethod.name} \n*/\n" + s }
            .map { s ->
                val firstClass = s.indexOf("class ") to "class ".length
                val firstInterface = s.indexOf("interface ") to "interface ".length
                val classNameLocation = sequenceOf(firstClass, firstInterface)
                    .filter { it.first >= 0 }
                    .map { it.first + it.second }
                    .flatMap {
                        sequenceOf(
                            s.indexOf("\n", it + 1),
                            s.indexOf(" ", it + 1),
                            s.indexOf("(", it + 1),
                            s.indexOf("{", it + 1),
                            s.indexOf(":", it + 1),
                            s.indexOf("<", it + 1),
                        )
                            .map { it1 -> it to it1 }
                    }
                    .filter { it.second >= 0 }
                    .minBy { it.second }
                val className = s.substring(classNameLocation.first - 1, classNameLocation.second)
                    .trim()
                    .replaceFirst(Regex("<.*>"), "")
                    .trim()
                val file = kc.baseDir
                    .resolve(testPackage.replace('.', File.separatorChar))
                    .resolve("$className.kt")
                Files.createDirectories(file.parent)
                Files.deleteIfExists(file)
                Files.writeString(file, s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
                file
            }
            .toList()

        try {
            val cl = kc.withSrc(sourceList).compile()
            compileResult = TestUtils.ProcessingResult.Success(cl)
        } catch (e: CompilationErrorException) {
            compileResult = TestUtils.ProcessingResult.Failure(e.messages)
        }
        return compileResult
    }

    protected fun new(name: String, vararg args: Any?) = loadClass(name).constructors[0].newInstance(*args)!!

    interface GeneratedObject<T> : () -> T

    protected fun newGenerated(name: String, vararg args: Any?) = object : GeneratedObject<Any> {
        override fun invoke() = loadClass(name).constructors[0].newInstance(*args)!!
    }

    protected fun newObject(name: String, vararg args: Any?): TestObject {
        val loadClass = loadClass(name)
        val inst = loadClass.constructors[0].newInstance(*args)!!
        return TestObject(loadClass.kotlin, inst)
    }

    class TestObject(
        val objectClass: KClass<*>,
        val objectInstance: Any
    ) {

        @SuppressWarnings("unchecked")
        fun <T> invoke(method: String, vararg args: Any?): T? {
            for (repositoryClassMethod in objectClass.memberFunctions) {
                if (repositoryClassMethod.name == method && repositoryClassMethod.parameters.size == args.size + 1) {
                    try {
                        val realArgs = Array(args.size + 1) {
                            if (it == 0) {
                                objectInstance
                            } else {
                                args[it - 1]
                            }
                        }

                        val result = if (repositoryClassMethod.isSuspend) {
                            runBlocking { repositoryClassMethod.callSuspend(*realArgs) }
                        } else {
                            repositoryClassMethod.call(*realArgs)
                        }
                        return when (result) {
                            is Mono<*> -> result.block()
                            is Future<*> -> result.get()
                            else -> result
                        } as T?
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            }
            throw IllegalArgumentException()
        }
    }


}
