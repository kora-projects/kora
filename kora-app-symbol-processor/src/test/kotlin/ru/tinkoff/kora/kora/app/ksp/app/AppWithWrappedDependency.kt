package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.application.graph.Wrapped

@KoraApp
interface AppWithWrappedDependency {
    fun class2(class1: Class1): Class2 {
        return Class2()
    }

    fun class3(class1: ValueOf<Class1>): Class3 {
        return Class3()
    }

    fun class4(class1: All<ValueOf<Class1>>): Class4 {
        return Class4()
    }

    fun class2ValueWrapped(class1: Wrapped<Class1>): Class2 {
        return Class2()
    }

    fun class3Wrapped(class1: ValueOf<Wrapped<Class1>>): Class3 {
        return Class3()
    }

    fun class4Wrapped(class1: All<ValueOf<Wrapped<Class1>>>): Class4 {
        return Class4()
    }

    fun class1(): Wrapped<Class1> {
        val c1 = Class1()
        return Wrapped { c1 }
    }

    class Class1
    class Class2 : MockLifecycle
    class Class3 : MockLifecycle
    class Class4 : MockLifecycle
}
