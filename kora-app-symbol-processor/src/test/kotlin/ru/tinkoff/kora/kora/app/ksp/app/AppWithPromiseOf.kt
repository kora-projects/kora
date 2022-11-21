package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.PromiseOf
import java.util.*

@KoraApp
interface AppWithPromiseOf {
    fun class1(): Class1 {
        return Class1()
    }

    fun class2(class1PromiseOf: PromiseOf<Class1>): Class2 {
        return Class2(class1PromiseOf)
    }

    fun class3(): Class3 {
        return Class3()
    }

    fun class4(class1PromiseOf: PromiseOf<Class3>, stringPromiseOf: PromiseOf<String>?): Class4 {
        return Class4(class1PromiseOf)
    }

    fun mock(class2: Class2, class4: Class4): MockLifecycle {
        return object : MockLifecycle {}
    }

    class Class1
    data class Class2(val promiseOf: PromiseOf<Class1>)

    class Class3 : MockLifecycle
    data class Class4(val promiseOf: PromiseOf<Class3>) : MockLifecycle
}
