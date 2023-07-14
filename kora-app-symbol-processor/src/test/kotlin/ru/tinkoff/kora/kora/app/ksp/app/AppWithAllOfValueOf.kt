package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import java.util.*

@KoraApp
interface AppWithAllOfValueOf {
    @Root
    fun class1(cls: All<ValueOf<Class2>>): Class1 {
        for (cl in cls) {
            Objects.requireNonNull(cl.get())
        }
        return Class1()
    }

    fun class2(): Class2 {
        return Class2()
    }

    class Class1
    class Class2
}
