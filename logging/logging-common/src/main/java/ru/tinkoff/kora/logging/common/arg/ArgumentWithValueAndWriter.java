package ru.tinkoff.kora.logging.common.arg;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.common.JsonWriter;
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
