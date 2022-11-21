package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithValueOfComponents {
    fun class1(class2: ValueOf<TestInterface?>): Class1 {
        return Class1(class2)
    }

    fun class2(class3: ValueOf<Class3>): Class2 {
        return Class2(class3.get())
    }

    fun class3(): Class3 {
        return Class3()
    }

    interface TestInterface

    data class Class1(val class2: ValueOf<TestInterface?>) : MockLifecycle

    data class Class2(val class3: Class3) : MockLifecycle, TestInterface

    class Class3
}
