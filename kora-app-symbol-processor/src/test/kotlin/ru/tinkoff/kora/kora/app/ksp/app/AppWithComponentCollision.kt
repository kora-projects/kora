package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithComponentCollision {
    fun c1(): Class1 {
        return Class1()
    }

    fun c2(): Class1 {
        return Class1()
    }

    fun c3(): Class1 {
        return Class1()
    }

    class Class1 : MockLifecycle
}
