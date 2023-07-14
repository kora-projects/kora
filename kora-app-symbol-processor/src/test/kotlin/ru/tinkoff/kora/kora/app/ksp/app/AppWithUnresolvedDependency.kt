package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithUnresolvedDependency {
    @Root
    fun class1(class2: Class2): Class1 {
        return Class1(class2)
    }

    fun class2(class3: Class3): Class2 {
        return Class2(class3)
    }

    data class Class1(val class2: Class2)

    data class Class2(val class3: Class3)

    open class Class3
}
