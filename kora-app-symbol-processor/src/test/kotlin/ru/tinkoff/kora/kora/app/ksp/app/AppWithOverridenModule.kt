package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithOverridenModule : Module2 {
    @Root
    fun class1(class2: Class2): Class1 {
        return Class1(class2)
    }

    data class Class1(val class2: Class2)

    class Class2
}

interface Module {
    fun class2(): AppWithOverridenModule.Class2 {
        throw IllegalStateException()
    }
}

interface Module2 : Module {
    override fun class2(): AppWithOverridenModule.Class2 {
        return AppWithOverridenModule.Class2()
    }
}
