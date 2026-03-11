package io.koraframework.kora.app.ksp.app

import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root

@KoraApp
interface AppWithComponentCollision {
    @Root
    fun c1(): Class1 {
        return Class1()
    }

    @Root
    fun c2(): Class1 {
        return Class1()
    }

    @Root
    fun c3(): Class1 {
        return Class1()
    }

    class Class1
}
