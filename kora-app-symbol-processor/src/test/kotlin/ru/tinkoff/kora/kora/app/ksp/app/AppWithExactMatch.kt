package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraSubmodule
import org.mockito.Mockito
import ru.tinkoff.kora.application.graph.Wrapped

@KoraApp
interface AppWithExactMatch {
    fun mockSuper1(superclass: Superclass1): MockLifecycle {
        return Mockito.mock(MockLifecycle::class.java)
    }

    fun mock1(superclass: Class1): MockLifecycle {
        return Mockito.mock(MockLifecycle::class.java)
    }

    fun mockSuper2(superclass: Superclass2): MockLifecycle {
        return Mockito.mock(MockLifecycle::class.java)
    }

    fun mock2(superclass: Class2): MockLifecycle {
        return Mockito.mock(MockLifecycle::class.java)
    }

    fun class1(): Class1 {
        return Class1()
    }

    fun class2(): Class2 {
        return Class2()
    }

    fun superclass(): Superclass1 {
        return Superclass1()
    }

    fun superclass2(): Wrapped<Superclass2> {
        return Wrapped { Superclass2() }
    }

    open class Superclass1
    class Class1 : Superclass1()
    open class Superclass2
    class Class2 : Superclass2()
}
