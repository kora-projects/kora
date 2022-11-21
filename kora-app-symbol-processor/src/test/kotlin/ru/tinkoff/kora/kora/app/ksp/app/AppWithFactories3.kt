package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithFactories3 {
    fun intComponent(): Int {
        return 0
    }

    fun <T> factory(typeRef: TypeRef<T>, dependency: Int): GenericClass<T> {
        return GenericClass()
    }

    fun genericClass1(): GenericClass<Class1> {
        return GenericClass()
    }

    fun class1(class1: GenericClass<Class1>): Class1 {
        return Class1()
    }

    class GenericClass<T>
    class Class1 : MockLifecycle
}
