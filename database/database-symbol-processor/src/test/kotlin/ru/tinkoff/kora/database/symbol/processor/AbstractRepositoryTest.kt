package ru.tinkoff.kora.database.symbol.processor

import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Future

abstract class AbstractRepositoryTest : AbstractSymbolProcessorTest() {
    class TestRepository(private val repositoryClass: Class<*>, private val repositoryObject: Any) {
        operator fun invoke(method: String, vararg args: Any?): Any? {
            for (repositoryClassMethod in repositoryClass.methods) {
                if (repositoryClassMethod.name == method && repositoryClassMethod.parameters.size == args.size) {
                    val result = repositoryClassMethod.invoke(repositoryObject, *args)
                    return when (result) {
                        is Mono<*> -> result.block()
                        is Future<*> -> result.get()
                        else -> result
                    }
                }
            }
            throw IllegalArgumentException()
        }
    }

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.common.annotation.*;
            import ru.tinkoff.kora.database.common.*;
            
            """.trimIndent()
    }

    protected fun compile(connectionFactory: Any, arguments: List<*>, @Language("kotlin") vararg sources: String): TestRepository {
        val compileResult = compile(listOf(RepositorySymbolProcessorProvider()), *sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        return try {
            val repositoryClass = compileResult.loadClass("\$TestRepository_Impl")
            val realArgs = arrayOfNulls<Any>(arguments.size + 1)
            realArgs[0] = connectionFactory
            System.arraycopy(arguments.toTypedArray(), 0, realArgs, 1, arguments.size)
            val repository = repositoryClass.constructors[0].newInstance(*realArgs)
            TestRepository(repositoryClass, repository)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        } catch (e: InstantiationException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        }
    }
}
