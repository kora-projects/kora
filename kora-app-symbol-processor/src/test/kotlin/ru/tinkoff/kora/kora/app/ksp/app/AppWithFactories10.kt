package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import java.io.Closeable

@KoraApp
interface AppWithFactories10 {
    fun mock1(registry: Closeable): MockLifecycle {
        return object : MockLifecycle {}
    }
}
