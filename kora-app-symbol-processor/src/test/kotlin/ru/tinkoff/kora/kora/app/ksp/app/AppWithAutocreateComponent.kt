package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithAutocreateComponent {
    @Root
    fun class1(class2: Class2): Class1 {
        return Class1(class2)
    }

    class Class1(private val class2: Class2)

    class Class2
}
