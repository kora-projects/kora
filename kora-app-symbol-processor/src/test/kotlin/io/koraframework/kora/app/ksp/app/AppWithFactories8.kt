package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.TypeRef
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

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
