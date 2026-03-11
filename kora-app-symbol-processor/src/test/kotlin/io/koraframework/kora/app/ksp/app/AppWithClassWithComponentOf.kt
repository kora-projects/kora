package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.ValueOf
import io.koraframework.common.Component
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

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
