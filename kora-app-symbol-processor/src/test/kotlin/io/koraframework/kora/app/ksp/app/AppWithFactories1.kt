package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.TypeRef
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithFactories1 {
    fun intComponent(): Int {
        return 0
    }

    fun <T> factory(typeRef: TypeRef<T>, dependency: Int): GenericClass<T> {
        return GenericClass()
    }

    @Root
    fun class1(class1: GenericClass<Class1>): Class1 {
        return Class1()
    }

    class GenericClass<T>
    class Class1
}
