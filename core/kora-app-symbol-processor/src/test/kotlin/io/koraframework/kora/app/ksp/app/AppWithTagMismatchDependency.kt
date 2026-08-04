package io.koraframework.kora.app.ksp.app

import io.koraframework.common.annotation.KoraApp
import io.koraframework.common.annotation.Root
import io.koraframework.common.annotation.Tag

@KoraApp
interface AppWithTagMismatchDependency {

    @Root
    fun root(@Tag(RequiredTag::class) service: TestService): Any = service

    fun service(): TestService = TestService()

    class RequiredTag

    class TestService
}
