package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraApp

@KoraApp
interface AppWithAutocreateComponent {
    fun class1(class2: Class2): Class1 {
        return Class1(class2)
    }

    class Class1(private val class2: Class2) : MockLifecycle

    class Class2
}
