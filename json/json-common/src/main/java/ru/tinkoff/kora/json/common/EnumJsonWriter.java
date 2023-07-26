package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

public final class EnumJsonWriter<T extends Enum<T>, V> implements JsonWriter<T> {
    private final RawJson[] values;

    public EnumJsonWriter(T[] values, Function<T, V> valueExtractor, JsonWriter<V> valueWriter) {
        this.values = new RawJson[values.length];
        for (int i = 0; i < values.length; i++) {
            var enumValue = values[i];
            var value = valueExtractor.apply(enumValue);
            try {
                var bytes = valueWriter.toByteArray(value);
                this.values[i] = new RawJson(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void write(JsonGenerator gen, @Nullable T object) throws IOException {
        if (object == null) {
            gen.writeNull();
            return;
        }
        gen.writeRawValue(this.values[object.ordinal()]);
    }
}
