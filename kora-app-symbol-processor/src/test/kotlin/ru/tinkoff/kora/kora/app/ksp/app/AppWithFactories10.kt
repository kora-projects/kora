package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import java.io.Closeable

@KoraApp
interface AppWithFactories10 {
    @Root
    fun mock1(registry: Closeable) = Any()
}
