package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.Tag
import java.lang.IllegalStateException

@KoraApp
interface AppWithFactories7 {
    fun intComponent(): Int {
        return 0
    }

    fun <T> factory1(typeRef: TypeRef<T>, dependency: Int): GenericClass<T> {
        throw IllegalStateException()
    }

    @Tag(Class1::class)
    fun <T> factory2(typeRef: TypeRef<T>, dependency: Int): GenericClass<T> {
        return GenericClass()
    }

    fun class1(@Tag(Class1::class) class1: GenericClass<Class1>): Class1 {
        return Class1()
    }

    class GenericClass<T>
    class Class1 : MockLifecycle
}
