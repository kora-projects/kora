package ru.tinkoff.kora.database.symbol.processor

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.KotlinCompilation

abstract class AbstractRepositoryTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.common.annotation.*;
            import ru.tinkoff.kora.database.common.*;
            
            """.trimIndent()
    }

    protected fun compile(connectionFactory: Any, arguments: List<*>, @Language("kotlin") vararg sources: String): TestObject {
        KotlinCompilation()
            .withPartialClasspath()
            .withClasspathJar("java-driver-core")
            .compile(listOf(RepositorySymbolProcessorProvider()), *sources)
            .assertSuccess()

        val realArgs = arrayOfNulls<Any>(arguments.size + 1)
        realArgs[0] = connectionFactory
        System.arraycopy(arguments.toTypedArray(), 0, realArgs, 1, arguments.size)
        for ((i, arg) in realArgs.withIndex()) {
            if (arg is GeneratedObject<*>) {
                realArgs[i] = arg.invoke()
            }
        }

        val repositoryClass = loadClass("\$TestRepository_Impl")
        val repository = repositoryClass.constructors[0].newInstance(*realArgs)
        return TestObject(repositoryClass.kotlin, repository)
    }

    protected fun compileForArgs(arguments: Array<Any?>, @Language("kotlin") vararg sources: String): TestObject {
        compile0(listOf(RepositorySymbolProcessorProvider()), *sources)
            .assertSuccess()

        val repositoryClass = loadClass("\$TestRepository_Impl")
        val repository = repositoryClass.constructors[0].newInstance(*arguments)
        return TestObject(repositoryClass.kotlin, repository)
    }
}
