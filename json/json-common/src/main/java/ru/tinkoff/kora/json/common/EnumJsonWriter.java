package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Function;

public final class EnumJsonWriter<T extends Enum<T>> implements JsonWriter<T> {
    private final SerializedString[] values;

    public EnumJsonWriter(T[] values, Function<T, String> mapper) {
        this.values = new SerializedString[values.length];
        for (int i = 0; i < values.length; i++) {
            this.values[i] = new SerializedString(mapper.apply(values[i]));
        }
    }

    @Override
    public void write(JsonGenerator gen, @Nullable T object) throws IOException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(this.values[object.ordinal()]);
        }
    }
}
