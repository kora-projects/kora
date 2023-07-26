package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RawJsonWriter implements JsonWriter<RawJson> {
    @Override
    public void write(JsonGenerator gen, @Nullable RawJson object) throws IOException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeRawValue(object);
        }
    }

    @Override
    public byte[] toByteArray(@Nullable RawJson object) throws IOException {
        if (object == null) {
            return "null".getBytes(StandardCharsets.ISO_8859_1);
        }
        return object.value;
    }
}
