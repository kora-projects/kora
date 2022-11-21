package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import java.util.*

interface AppWithInheritanceComponentsHelper {
    data class Class1( val class2: Class2) : MockLifecycle

    data class Class2( val class3: Class3) : MockLifecycle

    class Class3 : MockLifecycle
    interface AppWithInheritanceComponentsModule1 {
        fun class1(class2: Class2): Class1 {
            return Class1(class2)
        }
    }

    interface AppWithInheritanceComponentsModule2 {
        fun class2(class3: Class3): Class2 {
            return Class2(class3)
        }
    }

    interface AppWithInheritanceComponentsModule3 {
        fun class3(): Class3 {
            return Class3()
        }
    }
}
