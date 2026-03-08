package io.koraframework.kora.app.ksp.app

import io.koraframework.common.KoraApp
import io.koraframework.common.annotation.Root
import java.io.Closeable

@KoraApp
interface AppWithFactories10 {
    @Root
    fun mock1(registry: Closeable) = Any()
}
