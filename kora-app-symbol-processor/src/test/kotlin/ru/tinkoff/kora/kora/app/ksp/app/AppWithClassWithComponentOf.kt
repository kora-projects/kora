package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithClassWithComponentOf {
    @Root
    fun object1(class1: Class1) = Any()

    @Root
    fun object2(class1: ValueOf<Class3>) = Any()

    @Component
    class Class1(private val class2: Class2)

    @Component
    class Class2

    @Component
    class Class3
}
