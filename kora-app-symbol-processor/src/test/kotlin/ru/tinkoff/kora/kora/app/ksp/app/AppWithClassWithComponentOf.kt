package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraSubmodule
import org.mockito.Mockito
import reactor.core.publisher.Mono
import ru.tinkoff.kora.application.graph.Lifecycle
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.Component

@KoraApp
interface AppWithClassWithComponentOf {
    fun object1(class1: Class1): MockLifecycle {
        return Mockito.spy(MockLifecycle::class.java)
    }

    fun object2(class1: ValueOf<Class3>): MockLifecycle {
        return Mockito.spy(MockLifecycle::class.java)
    }

    @Component
    class Class1(private val class2: Class2) : Lifecycle {
        override fun init(): Mono<Void> {
            return Mono.empty()
        }

        override fun release(): Mono<Void> {
            return Mono.empty()
        }
    }

    @Component
    class Class2

    @Component
    class Class3
}
