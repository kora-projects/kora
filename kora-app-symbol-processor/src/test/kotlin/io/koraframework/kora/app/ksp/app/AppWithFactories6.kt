package io.koraframework.kora.app.ksp.app

import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithFactories6 {
    fun <T> factory1(genericClass: GenericClass<T>): GenericClass<T> {
        return GenericClass()
    }

    @Root
    fun class2(class1: GenericClass<Class1>): Class2 {
        return Class2()
    }

    class GenericClass<T>
    class Class1
    class Class2
}
