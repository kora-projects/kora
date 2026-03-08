package io.koraframework.kora.app.ksp.app

import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root
import java.io.Closeable

@KoraApp
interface AppWithFactories11 {
    @Root
    fun mock1(`object`: GenericClass<String>) = Any()

    fun <T> factory1(t: Closeable): GenericClass<T> {
        return GenericClass()
    }

    fun <T> factory2(t: Long): GenericClass<T> {
        return GenericClass()
    }

    open class GenericClass<T>
}
