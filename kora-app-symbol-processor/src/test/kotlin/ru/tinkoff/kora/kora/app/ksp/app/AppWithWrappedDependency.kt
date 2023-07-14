package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.application.graph.Wrapped
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithWrappedDependency {
    @Root
    fun class2(class1: Class1): Class2 {
        return Class2()
    }

    @Root
    fun class3(class1: ValueOf<Class1>): Class3 {
        return Class3()
    }

    @Root
    fun class4(class1: All<ValueOf<Class1>>): Class4 {
        return Class4()
    }

    @Root
    fun class2ValueWrapped(class1: Wrapped<Class1>): Class2 {
        return Class2()
    }

    @Root
    fun class3Wrapped(class1: ValueOf<Wrapped<Class1>>): Class3 {
        return Class3()
    }

    @Root
    fun class4Wrapped(class1: All<ValueOf<Wrapped<Class1>>>): Class4 {
        return Class4()
    }

    fun class1(): Wrapped<Class1> {
        val c1 = Class1()
        return Wrapped { c1 }
    }

    class Class1
    class Class2
    class Class3
    class Class4
}
