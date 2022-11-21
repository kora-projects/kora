package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithFactories4 {
    fun intComponent(): Int {
        return 0
    }

    fun <T : List<Class1>> factory(
        typeRef1: TypeRef<T>,
        typeRef2: TypeRef<Class2>,
        dependency: Int
    ): TwoGenericClass<T, Class2> {
        return TwoGenericClass()
    }

    fun class1(genericClass: TwoGenericClass<List<Class1>, Class2>): Class1 {
        return Class1()
    }

    class TwoGenericClass<T, Q>
    class Class1 : MockLifecycle
    class Class2 : MockLifecycle
}
