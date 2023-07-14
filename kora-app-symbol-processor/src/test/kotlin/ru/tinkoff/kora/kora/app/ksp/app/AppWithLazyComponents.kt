package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithLazyComponents {
    fun class0(): Class0 {
        return Class0()
    }

    fun class1(class0: Class0): Class1 {
        return Class1()
    }

    @Root
    fun class2(class1: Class1): Class2 {
        return Class2()
    }

    fun class3(class2: Class2): Class3 {
        throw RuntimeException()
    }

    class Class0
    class Class1
    class Class2
    class Class3
}
