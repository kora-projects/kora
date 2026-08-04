package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.annotation.Root;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.common.annotation.KoraApp;

@KoraApp
public interface AppWithMissingJsonWriter {

    @Root
    default Object root(JsonWriter<TestEvent> writer) {
        return writer;
    }

    record TestEvent(String value) {}
}
