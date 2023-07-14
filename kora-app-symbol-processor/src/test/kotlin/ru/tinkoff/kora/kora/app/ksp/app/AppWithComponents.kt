package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithComponents {
    @Root
    fun class1(class2: Class2) = Class1(class2)

    @Root
    fun class2(class3: Interface1) = Class2(class3)

    fun class3() = Class3()

    @Root
    fun genericExtends(class3: Class3) = GenericClass(class3)

    @Root
    fun genericSuper(class3: Class3) = GenericClass(class3)

    data class Class1(val class2: Class2)

    data class Class2(val class3: Interface1)

    interface Interface1
    class Class3 : Interface1
    data class GenericClass<T>(val t: T)
}
