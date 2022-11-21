package ru.tinkoff.kora.kora.app.ksp.app

import reactor.core.publisher.Mono
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import java.time.Duration
import java.util.*

@KoraApp
interface AppWithAllOfValueOf {
    fun class1(cls: All<ValueOf<Class2>>): Class1 {
        for (cl in cls) {
            Objects.requireNonNull(cl.get())
        }
        return Class1()
    }

    fun class2(): Class2 {
        return Class2()
    }

    class Class1 : MockLifecycle
    class Class2 : MockLifecycle {
        override fun init(): Mono<Void> {
            return Mono.delay(Duration.ofSeconds(1)).then()
        }
    }
}
