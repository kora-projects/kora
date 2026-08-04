package io.koraframework.kora.app.ksp.app

import io.koraframework.common.annotation.Root
import io.koraframework.json.common.JsonWriter
import io.koraframework.common.annotation.KoraApp

@KoraApp
interface AppWithMissingJsonWriter {

    @Root
    fun root(writer: JsonWriter<TestEvent>): Any = writer

    data class TestEvent(val value: String)
}
