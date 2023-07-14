package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithFactories5 {
    fun intComponent(): Int {
        return 0
    }

    fun <T : List<Class1>> factory1(
        typeRef1: TypeRef<T>,
        typeRef2: TypeRef<Class2>,
        dependency: Int
    ): TwoGenericClass<T, Class2> {
        return TwoGenericClass()
    }

    fun <T, Q> factory2(
        typeRef1: TypeRef<T>,
        typeRef2: TypeRef<Q>,
        nonExistDependency: String
    ): TwoGenericClass<T, Q> {
        return TwoGenericClass()
    }

    fun class1(genericClass: TwoGenericClass<List<Class1>, Class2>): Class1 {
        return Class1()
    }

    class TwoGenericClass<T, Q>
    class Class1
    class Class2
}
