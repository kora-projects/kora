package io.koraframework.logging.common.arg;

import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

record ArgumentWithValueAndWriter<T>(String fieldName, @Nullable T value, JsonWriter<T> writer) implements StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) {
        if (this.value == null) {
            generator.writeNull();
        } else {
            this.writer.write(generator, this.value);
        }
    }
}
