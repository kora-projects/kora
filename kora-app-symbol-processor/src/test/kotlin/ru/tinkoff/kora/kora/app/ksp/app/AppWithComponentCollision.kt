package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithComponentCollision {
    @Root
    fun c1(): Class1 {
        return Class1()
    }

    @Root
    fun c2(): Class1 {
        return Class1()
    }

    @Root
    fun c3(): Class1 {
        return Class1()
    }

    class Class1
}
