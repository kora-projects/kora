package ru.tinkoff.kora.kora.app.ksp

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithComponentsKotlin {

    @Root
    fun fromClassFromSomePackage(class1: ru.tinkoff.kora.kora.app.ksp.somepackage.Class1): Class10 {
        return Class10(class1)
    }

    @Root
    fun fromClassFromOtherPackage(class1: ru.tinkoff.kora.kora.app.ksp.otherpackage.Class1): Class9 {
        return Class9(class1)
    }

    @Root
    fun class2(class3: Interface1): Class2 {
        return Class2()
    }

    @Root
    fun class3(cl4: Class4?): Class3 {
        return Class3()
    }

    @Root
    fun genericExtends(class3: Class3): GenericClass<out Class3> {
        return GenericClass(class3)
    }

    @Root
    fun genericSuper(class3: Class3): GenericClass<in Class3> {
        return GenericClass(class3)
    }

    @Root
    fun class8(class7: Class7) = Class8()

    class Class1
    class Class2
    interface Interface1
    class Class3 : Interface1
    class Class4
    class Class7(class4: Class4?)
    class Class8
    class Class9(val class1: ru.tinkoff.kora.kora.app.ksp.otherpackage.Class1)
    class Class10(val class1: ru.tinkoff.kora.kora.app.ksp.somepackage.Class1)
    class GenericClass<T>(val t: T)
}
