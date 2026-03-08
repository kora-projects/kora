package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.TypeRef
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithFactories2 {
    @Root
    fun class1(dependency: GenericClass<List<Class1>, String>): Class1 {
        return Class1()
    }

    @Root
    fun <T> factory2(typeRef: TypeRef<T>): GenericClassImpl<List<T>> {
        return GenericClassImpl()
    }

    open class GenericClass<T, Q>
    class GenericClassImpl<T> : GenericClass<T, String>()
    class Class1
}
