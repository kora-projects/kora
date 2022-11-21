package ru.tinkoff.kora.kora.app.ksp

import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithComponentsKotlin {

    fun fromClassFromSomePackage(class1: ru.tinkoff.kora.kora.app.ksp.somepackage.Class1): Class10 {
        return Class10(class1)
    }

    fun fromClassFromOtherPackage(class1: ru.tinkoff.kora.kora.app.ksp.otherpackage.Class1): Class9 {
        return Class9(class1)
    }

    fun class2(class3: Interface1): Class2 {
        return Class2()
    }

    fun class3(cl4: Class4?): Class3 {
        return Class3()
    }

    fun genericExtends(class3: Class3): GenericClass<out Class3> {
        return GenericClass(class3)
    }

    fun genericSuper(class3: Class3): GenericClass<in Class3> {
        return GenericClass(class3)
    }

    fun class8(class7: Class7) = Class8()

    class Class1 : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class2 : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    interface Interface1
    class Class3 : Interface1, ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class4 : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class7(class4: Class4?) : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class8 : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class9(val class1: ru.tinkoff.kora.kora.app.ksp.otherpackage.Class1) : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class Class10(val class1: ru.tinkoff.kora.kora.app.ksp.somepackage.Class1) : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
    class GenericClass<T>(val t: T) : ru.tinkoff.kora.annotation.processor.common.MockLifecycle
}
