package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.Wrapped
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithExactMatch {
    @Root
    fun mockSuper1(superclass: Superclass1) = Any()

    @Root
    fun mock1(superclass: Class1) = Any()

    @Root
    fun mockSuper2(superclass: Superclass2) = Any()

    @Root
    fun mock2(superclass: Class2) = Any()

    fun class1() = Class1()

    fun class2() = Class2()

    fun superclass() = Superclass1()

    fun superclass2() = Wrapped { Superclass2() }

    open class Superclass1
    class Class1 : Superclass1()
    open class Superclass2
    class Class2 : Superclass2()
}
