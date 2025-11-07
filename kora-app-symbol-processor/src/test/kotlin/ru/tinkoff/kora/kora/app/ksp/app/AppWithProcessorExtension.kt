package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithProcessorExtension {
    annotation class TestAnnotation

    @Root
    fun mockLifecycle(interface1: Interface1): Any {
        return Any()
    }

    @TestAnnotation
    interface Interface1
}
