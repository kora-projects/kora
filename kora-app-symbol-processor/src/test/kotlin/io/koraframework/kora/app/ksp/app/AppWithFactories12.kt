package io.koraframework.kora.app.ksp.app

import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithFactories12 {

    fun <T, R> factory1(t: SimpleGeneric<T>, r: SimpleGeneric<R>): GenericClass<T, R> {
        return GenericClass(t, r)
    }

    fun factory2(): SimpleGeneric<Long> {
        return SimpleGeneric()
    }

    fun factory3(): SimpleGeneric<String> {
        return SimpleGeneric()
    }

    @Root
    fun mock1(cl: GenericClass<Long, String>): Any = Any()

    open class SimpleGeneric<T>
    data class GenericClass<T, R>(val t: SimpleGeneric<T>, val r: SimpleGeneric<R>)
}
