@file:OptIn(KspExperimental::class)

package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.squareup.kotlinpoet.asClassName
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

object DbTestUtils {
    fun <T : Any> compile(repository: KClass<T>, vararg params: Any?): T {
        return try {
            val cl = symbolProcess(repository, RepositorySymbolProcessorProvider())
            val clazz = cl.loadClass(repository.asClassName().packageName + ".$" + repository.simpleName + "_Impl")
            assert(clazz.constructors.size == 1)
            val constructor = clazz.constructors[0] as Constructor<out T>
            constructor.newInstance(*params)
        } catch (e: CompilationErrorException) {
            throw e
        } catch (throwable: RuntimeException) {
            throw throwable
        } catch (throwable: Throwable) {
            throw RuntimeException(throwable)
        }
    }
    fun <T: Any> compileClass(repository: KClass<T>): KClass<out T> {
        try {
            val cl = symbolProcess(repository, RepositorySymbolProcessorProvider())
            return cl.loadClass(repository.asClassName().packageName + ".$" + repository.simpleName + "_Impl").kotlin as KClass<out T>
        } catch (e: CompilationErrorException) {
            throw e
        } catch (throwable: RuntimeException) {
            throw throwable
        } catch (throwable: Throwable) {
            throw RuntimeException(throwable)
        }
    }
}
