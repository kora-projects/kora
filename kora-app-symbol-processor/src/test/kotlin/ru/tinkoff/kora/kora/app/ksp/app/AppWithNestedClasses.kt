package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle

@KoraApp
interface AppWithNestedClasses {
    fun nested1(): Root1.Nested {
        return Root1.Nested()
    }

    fun nested2(): Root2.Nested {
        return Root2.Nested()
    }

    class Root1 {
        class Nested : MockLifecycle
    }

    interface Root2 {
        class Nested : MockLifecycle
    }
}
