package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.TypeRef

@KoraApp
interface AppWithFactories8 {
    fun mock(`object`: GenericInterface<Int, String>): MockLifecycle {
        return object : MockLifecycle {}
    }

    fun <T : Comparable<T>> impl(t: TypeRef<T>): GenericImpl<T> {
        return GenericImpl()
    }

    interface GenericInterface<T1, T2>
    class GenericImpl<T> : GenericInterface<T, String>
}
