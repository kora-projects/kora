package ru.tinkoff.kora.http.client.common.request.mapper;

import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;
import ru.tinkoff.kora.json.common.JsonWriter;

public class JsonStringParameterConverter<T> implements StringParameterConverter<T> {
    private final JsonWriter<T> writer;

    public JsonStringParameterConverter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public String convert(T value) {
        try {
            var bytes = this.writer.toByteArray(value);
            return new String(bytes);
        } catch (Exception e) {
            throw new HttpClientEncoderException(e);
        }
    }
}
