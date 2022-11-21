package ru.tinkoff.kora.logging.common.arg;

import com.fasterxml.jackson.core.JsonGenerator;
import ru.tinkoff.kora.json.common.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;

record ArgumentWithValueAndWriter<T>(String fieldName, @Nullable T value, JsonWriter<T> writer) implements StructuredArgument {
    @Override
    public void writeTo(JsonGenerator generator) throws IOException {
        if (this.value == null) {
            generator.writeNull();
        } else {
            this.writer.write(generator, this.value);
        }
    }
}
