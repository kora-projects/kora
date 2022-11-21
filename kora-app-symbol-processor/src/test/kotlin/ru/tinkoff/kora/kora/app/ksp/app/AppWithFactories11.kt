package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import java.io.Closeable

@KoraApp
interface AppWithFactories11 {
    fun mock1(`object`: GenericClass<String>): MockLifecycle {
        return object : MockLifecycle {}
    }

    fun <T> factory1(t: Closeable): GenericClass<T> {
        return GenericClass()
    }

    fun <T> factory2(t: Long): GenericClass<T> {
        return GenericClass()
    }

    open class GenericClass<T>
}
