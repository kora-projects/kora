package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithAllOfComponents {
    fun class1(): Class1 {
        return Class1()
    }

    fun class2(): Class2 {
        return Class2()
    }

    @Root
    fun class3(class4: Class4) = Class3(class4)

    fun class5() = Class5()

    @Tag(Superclass::class)
    fun class5WithTag() = Class5()

    @Root
    fun classWithAllOf(allOfSuperclass: All<Superclass>): ClassWithAllOf {
        return ClassWithAllOf(allOfSuperclass)
    }

    @Tag(Superclass::class)
    @Root
    fun classWithAllOfWithTag(
        @Tag(Superclass::class) allOfSuperclass: All<Superclass>
    ): ClassWithAllOf {
        return ClassWithAllOf(allOfSuperclass)
    }

    @Root
    fun classWithAllValueOf(allOfSuperclass: All<ValueOf<Superclass>>) = ClassWithAllValueOf(allOfSuperclass)

    @Root
    fun classWithinterfaces(allSomeInterfaces: All<SomeInterface>) = ClassWithInterfaces(allSomeInterfaces)

    @Root
    fun classWithInterfacesValueOf(allSomeInterfaces: All<ValueOf<SomeInterface>>) = ClassWithInterfacesValueOf(allSomeInterfaces)

    @Root
    fun classWithAllOfAnyTag(@Tag(Tag.Any::class) class5All: All<Class5>) = ClassWithAllOfAnyTag(class5All)
    open class Superclass
    class Class1 : Superclass(), SomeInterface
    class Class2 : Superclass(), SomeInterface
    class Class3(private val class4: Class4) : Superclass()
    class Class4 : Superclass()
    class Class5 : Superclass()
    data class ClassWithAllOf(val allOfSuperclass: All<Superclass>)

    data class ClassWithAllValueOf(val allOfSuperclass: All<ValueOf<Superclass>>)

    interface SomeInterface
    data class ClassWithInterfaces(val allSomeInterfaces: All<SomeInterface>)

    data class ClassWithInterfacesValueOf(val allSomeInterfaces: All<ValueOf<SomeInterface>>)

    data class ClassWithAllOfAnyTag(@Tag(Tag.Any::class) val class5All: All<Class5>)
}
