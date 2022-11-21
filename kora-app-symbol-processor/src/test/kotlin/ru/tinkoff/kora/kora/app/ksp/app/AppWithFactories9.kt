package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.TypeRef

@KoraApp
interface AppWithFactories9 {
    fun mock1(`object`: GenericInterface<String>): MockLifecycle {
        return object : MockLifecycle {}
    }

    fun mock2(`object`: GenericImpl<String>): MockLifecycle {
        return object : MockLifecycle {}
    }

    fun <T> factory1(t: TypeRef<T>): GenericInterface<T> {
        return GenericImpl()
    }

    fun <T> factory2(t: TypeRef<T>): GenericImpl<T> {
        return GenericImpl()
    }

    interface GenericInterface<T>
    class GenericImpl<T> : GenericInterface<T>
}
