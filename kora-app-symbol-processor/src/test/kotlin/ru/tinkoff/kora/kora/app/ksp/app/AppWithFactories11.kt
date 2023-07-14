package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
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
