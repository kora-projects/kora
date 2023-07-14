package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithCircularDependency {
    @Root
    fun class1(class2: Class2) = Class1()
    fun class2(class3: Class3): Class2
    fun class3(class1: Class1): Class3

    class Class1

    class Class2

    class Class3
}
