package ru.tinkoff.kora.http.client.common.writer;

import java.util.function.Function;

public final class EnumStringParameterConverter<T extends Enum<T>> implements StringParameterConverter<T> {
    private final String[] values;

    public EnumStringParameterConverter(T[] values, Function<T, String> mapper) {
        this.values = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            this.values[i] = mapper.apply(values[i]);
        }
    }

    @Override
    public String convert(T object) {
        if (object == null) {
            return null;
        } else {
            return this.values[object.ordinal()];
        }
    }
}
