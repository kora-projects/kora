package io.koraframework.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Future
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractSymbolProcessorTest {

    private val _testInfo = ThreadLocal<TestInfo>()
    protected var testInfo: TestInfo
        get() = _testInfo.get() ?: throw IllegalStateException("testInfo not initialized")
        set(value) = _testInfo.set(value)

    private val _testExecutionId = ThreadLocal<String>()
    private var testExecutionId: String
        get() = _testExecutionId.get() ?: throw IllegalStateException("testExecutionId not initialized")
        set(value) = _testExecutionId.set(value)

    private val _compileResult = ThreadLocal<TestUtils.ProcessingResult>()
    protected var compileResult: TestUtils.ProcessingResult
        get() = _compileResult.get() ?: throw IllegalStateException("compileResult is not initialized for current thread")
        set(value) = _compileResult.set(value)

    protected val isCompileResultInitialized: Boolean
        get() = _compileResult.get() != null

    private val _baseDir = ThreadLocal<Path>()
    private var baseDir: Path?
        get() = _baseDir.get()
        set(value) = _baseDir.set(value)

    private val _generatedSourcesPath = ThreadLocal<Path>()
    private var generatedSourcesPath: Path?
        get() = _generatedSourcesPath.get()
        set(value) = _generatedSourcesPath.set(value)

    protected val compileOptions: MutableMap<String, String> = mutableMapOf()

    @BeforeEach
    fun beforeEach(testInfo: TestInfo) {
        this._testInfo.set(testInfo)
        this._testExecutionId.set("run_" + UUID.randomUUID().toString().replace("-", ""))

        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()

        val path = Paths.get(".", "build", "in-test-generated-ksp", "sources")
            .resolve(testClass.getPackage().name.replace('.', '/'))
            .resolve("packageFor" + testClass.simpleName)
            .resolve(testMethod.name)
            .resolve(testExecutionId)

        this.generatedSourcesPath = path

        Files.createDirectories(path)
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun afterEach() {
        try {
            if (isCompileResultInitialized) {
                val cr = compileResult
                if (cr is TestUtils.ProcessingResult.Success && cr.classLoader is AutoCloseable) {
                    cr.classLoader.close()
                }
            }

            generatedSourcesPath?.deleteRecursively()
            baseDir?.deleteRecursively()
        } finally {
            _compileResult.remove()
            _testInfo.remove()
            _testExecutionId.remove()
            _generatedSourcesPath.remove()
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
            import io.koraframework.common.annotation.*;
            import io.koraframework.common.*;
            import org.jspecify.annotations.Nullable;
            
            """.trimIndent()
    }

    protected fun compile0(processors: List<SymbolProcessorProvider>, @Language("kotlin") vararg sources: String): TestUtils.ProcessingResult =
        compile0(processors, listOf(), *sources)

    /**
     * Java sources participate in the same compilation, which is the only way to reproduce
     * behavior that depends on Kotlin flexible/platform types.
     */
    protected fun compile0(
        processors: List<SymbolProcessorProvider>,
        @Language("java") javaSources: List<String>,
        @Language("kotlin") vararg sources: String
    ): TestUtils.ProcessingResult {
        val testPackage = testPackage()
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        val commonImports = commonImports()
        val kc = KotlinCompilation()
            .withProcessors(processors)
            .apply { processorsOptions.putAll(compileOptions) }

        this.baseDir = kc.baseDir
        val packageDir = kc.baseDir.resolve(testPackage.replace('.', File.separatorChar))

        val sourceList = sequenceOf(*sources)
            .map { s: String -> "package $testPackage;\n$commonImports\n/**\n* @see ${testClass.canonicalName}.${testMethod.name} \n*/\n" + s }
            .map { s ->
                val className = Regex("""\b(?:class|interface)\s+([A-Za-z_][A-Za-z0-9_]*)""")
                    .find(s)
                    ?.groupValues
                    ?.get(1)
                    ?: throw IllegalArgumentException("No class or interface declaration found in test source")
                val file = packageDir.resolve("$className.kt")
                Files.createDirectories(file.parent)
                Files.deleteIfExists(file)
                Files.writeString(file, s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
                file
            }
            .toList()

        val javaSourceList = javaSources
            .map { s -> "package $testPackage;\n" + s }
            .map { s ->
                val className = Regex("""\b(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)""")
                    .find(s)
                    ?.groupValues
                    ?.get(1)
                    ?: throw IllegalArgumentException("No class or interface declaration found in test java source")
                val file = kc.baseDir
                    .resolve(testPackage.replace('.', File.separatorChar))
                    .resolve("$className.java")
                Files.createDirectories(file.parent)
                Files.deleteIfExists(file)
                Files.writeString(file, s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
                file
            }

        try {
            val cl = kc.withSrc(sourceList).withJavaSrcs(javaSourceList).compile()
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

                        val result = repositoryClassMethod.call(*realArgs)
                        return when (result) {
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
