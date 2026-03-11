package io.koraframework.kora.app.ksp.app

import io.koraframework.application.graph.All
import io.koraframework.application.graph.ValueOf
import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root
import java.util.*

@KoraApp
interface AppWithAllOfValueOf {
    @Root
    fun class1(cls: All<ValueOf<Class2>>): Class1 {
        for (cl in cls) {
            Objects.requireNonNull(cl.get())
        }
        return Class1()
    }

    fun class2(): Class2 {
        return Class2()
    }

    class Class1
    class Class2
}
