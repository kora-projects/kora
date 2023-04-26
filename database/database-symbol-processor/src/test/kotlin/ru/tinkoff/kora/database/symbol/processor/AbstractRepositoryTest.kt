package ru.tinkoff.kora.database.symbol.processor

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

abstract class AbstractRepositoryTest : AbstractSymbolProcessorTest() {
    class TestRepository(val repositoryClass: KClass<*>, private val repositoryObject: Any) {
        @SuppressWarnings("unchecked")
        fun <T> invoke(method: String, vararg args: Any?): T? {
            for (repositoryClassMethod in repositoryClass.memberFunctions) {
                if (repositoryClassMethod.name == method && repositoryClassMethod.parameters.size == args.size + 1) {
                    try {
                        val realArgs = Array(args.size + 1) {
                            if (it == 0) {
                                repositoryObject
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
            for ((i, arg) in realArgs.withIndex()) {
                if (arg is GeneratedObject<*>) {
                    realArgs[i] = arg.invoke()
                }
            }
            val repository = repositoryClass.constructors[0].newInstance(*realArgs)
            TestRepository(repositoryClass.kotlin, repository)
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
