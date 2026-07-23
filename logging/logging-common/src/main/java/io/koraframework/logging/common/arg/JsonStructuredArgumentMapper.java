package io.koraframework.logging.common.arg;

import io.koraframework.json.common.JsonWriter;
import tools.jackson.core.JsonGenerator;

public final class JsonStructuredArgumentMapper<T> implements StructuredArgumentMapper<T> {
    private final JsonWriter<T> writer;

    public JsonStructuredArgumentMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, T value) {
        this.writer.write(gen, value);
    }
}
