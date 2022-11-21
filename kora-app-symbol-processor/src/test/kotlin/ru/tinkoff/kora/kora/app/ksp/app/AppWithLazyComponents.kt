package ru.tinkoff.kora.kora.app.ksp.app

import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.KoraApp
import java.lang.RuntimeException
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.Lifecycle
import ru.tinkoff.kora.application.graph.Wrapped

@KoraApp
interface AppWithLazyComponents {
    fun class0(): Class0 {
        return Class0()
    }

    fun class1(class0: Class0): Class1 {
        return Class1()
    }

    fun class2(class1: Class1): Class2 {
        return Class2()
    }

    fun class3(class2: Class2): Class3 {
        throw RuntimeException()
    }

    class Class0
    class Class1
    class Class2 : Lifecycle {
        override fun init(): Mono<Void> {
            return Mono.empty()
        }

        override fun release(): Mono<Void> {
            return Mono.empty()
        }
    }

    class Class3
}
