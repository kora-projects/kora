package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithFactories9 {
    @Root
    fun mock1(`object`: GenericInterface<String>) = Any()

    @Root
    fun mock2(`object`: GenericImpl<String>) = Any()

    fun <T> factory1(t: TypeRef<T>): GenericInterface<T> {
        return GenericImpl()
    }

    fun <T> factory2(t: TypeRef<T>): GenericImpl<T> {
        return GenericImpl()
    }

    interface GenericInterface<T>
    class GenericImpl<T> : GenericInterface<T>
}
