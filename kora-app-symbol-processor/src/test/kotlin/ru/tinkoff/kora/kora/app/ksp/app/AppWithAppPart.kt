package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.KoraSubmodule
import ru.tinkoff.kora.common.Tag

@KoraSubmodule
interface AppWithAppPart {
    fun class1(class2: Class2): Class1 {
        return Class1()
    }

    @Tag(Class1::class)
    fun class1Tag(class2: Class2): Class1 {
        return Class1()
    }

    class Class1
    class Class2

    @Component
    class Class3

    @ru.tinkoff.kora.common.Module
    interface Module {
        fun class2(): Class2 {
            return Class2()
        }
    }
}
