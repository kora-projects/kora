package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithNestedClasses {
    @Root
    fun nested1(): Root1.Nested {
        return Root1.Nested()
    }

    @Root
    fun nested2(): Root2.Nested {
        return Root2.Nested()
    }

    class Root1 {
        class Nested
    }

    interface Root2 {
        class Nested
    }
}
