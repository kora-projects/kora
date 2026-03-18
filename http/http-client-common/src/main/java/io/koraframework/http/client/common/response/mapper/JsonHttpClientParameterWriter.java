package io.koraframework.http.client.common.response.mapper;

import io.koraframework.http.client.common.exception.HttpClientEncoderException;
import io.koraframework.http.client.common.response.HttpClientParameterWriter;
import io.koraframework.json.common.JsonWriter;

public class JsonHttpClientParameterWriter<T> implements HttpClientParameterWriter<T> {
    private final JsonWriter<T> writer;

    public JsonHttpClientParameterWriter(JsonWriter<T> writer) {
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
