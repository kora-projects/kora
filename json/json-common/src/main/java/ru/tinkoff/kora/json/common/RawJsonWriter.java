package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

import java.nio.charset.StandardCharsets;

public class RawJsonWriter implements JsonWriter<RawJson> {
    @Override
    public void write(JsonGenerator gen, @Nullable RawJson object) {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeRawValue(object);
        }
    }

    @Override
    public byte[] toByteArray(@Nullable RawJson object) {
        if (object == null) {
            return "null".getBytes(StandardCharsets.ISO_8859_1);
        }
        return object.value;
    }
}
