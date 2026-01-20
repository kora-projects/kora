package ru.tinkoff.kora.kora.app.ksp

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithComponentsKotlin {

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

    @Component
    class Class7(class4: Class4?)
    class Class8
    class GenericClass<T>(val t: T)
}
