package io.koraframework.http.client.common.request.mapper;

import io.koraframework.http.client.common.HttpClientEncoderException;
import io.koraframework.http.client.common.writer.StringParameterConverter;
import io.koraframework.json.common.JsonWriter;

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
