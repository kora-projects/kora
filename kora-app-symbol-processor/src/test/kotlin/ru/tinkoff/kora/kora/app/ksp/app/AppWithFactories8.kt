package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithFactories8 {
    @Root
    fun mock(`object`: GenericInterface<Int, String>) = Any()

    fun <T : Comparable<T>> impl(t: TypeRef<T>): GenericImpl<T> {
        return GenericImpl()
    }

    interface GenericInterface<T1, T2>
    class GenericImpl<T> : GenericInterface<T, String>
}
