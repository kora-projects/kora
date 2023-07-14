package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithModuleOf {
    @Root
    fun class1(class2: Class2): Class1 {
        return Class1(class2)
    }

    fun class2(): Class3 {
        return Class3()
    }

    data class Class1(val class2: Class2)
    open class Class2
    class Class3

    @ru.tinkoff.kora.common.Module
    interface Module {
        fun class2(): Class2 {
            return Class2()
        }
    }
}
