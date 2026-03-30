package io.koraframework.http.client.common.request.mapper;

import io.koraframework.http.client.common.request.HttpClientParameterWriter;

import java.util.function.Function;

public final class EnumHttpClientParameterWriter<T extends Enum<T>> implements HttpClientParameterWriter<T> {
    private final String[] values;

    public EnumHttpClientParameterWriter(T[] values, Function<T, String> mapper) {
        this.values = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            this.values[i] = mapper.apply(values[i]);
        }
    }

    @Override
    public String convert(T value) {
        if (value == null) {
            return null;
        } else {
            return this.values[value.ordinal()];
        }
    }
}
