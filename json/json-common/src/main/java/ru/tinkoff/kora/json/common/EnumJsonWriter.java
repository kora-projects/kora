package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

import java.util.function.Function;

public final class EnumJsonWriter<T extends Enum<T>, V> implements JsonWriter<T> {
    private final RawJson[] values;

    public EnumJsonWriter(T[] values, Function<T, V> valueExtractor, JsonWriter<V> valueWriter) {
        this.values = new RawJson[values.length];
        for (int i = 0; i < values.length; i++) {
            var enumValue = values[i];
            var value = valueExtractor.apply(enumValue);
            var bytes = valueWriter.toByteArray(value);
            this.values[i] = new RawJson(bytes);
        }
    }

    @Override
    public void write(JsonGenerator gen, @Nullable T object) {
        if (object == null) {
            gen.writeNull();
            return;
        }
        gen.writeRawValue(this.values[object.ordinal()]);
    }
}
