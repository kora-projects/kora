package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class EnumStringParameterReader<T extends Enum<T>> implements StringParameterReader<T> {
    private final Map<String, T> values;

    public EnumStringParameterReader(T[] values, Function<T, String> mapper) {
        this.values = new HashMap<>();
        for (var value : values) {
            this.values.put(mapper.apply(value), value);
        }
    }

    @Override
    public T read(String string) {
        var value = this.values.get(string);
        if (value == null) {
            throw HttpServerResponseException.of(400, "Invalid value '%s'. Valid values are: %s".formatted(string, this.values.keySet()));
        }
        return value;
    }
}
