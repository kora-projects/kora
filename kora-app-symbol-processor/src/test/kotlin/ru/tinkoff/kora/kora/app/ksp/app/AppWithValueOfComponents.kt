package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithValueOfComponents {
    @Root
    fun class1(class2: ValueOf<TestInterface?>): Class1 {
        return Class1(class2)
    }

    @Root
    fun class2(class3: ValueOf<Class3>): Class2 {
        return Class2(class3.get())
    }

    @Root
    fun class3(): Class3 {
        return Class3()
    }

    interface TestInterface

    data class Class1(val class2: ValueOf<TestInterface?>)

    data class Class2(val class3: Class3) : TestInterface

    class Class3
}
