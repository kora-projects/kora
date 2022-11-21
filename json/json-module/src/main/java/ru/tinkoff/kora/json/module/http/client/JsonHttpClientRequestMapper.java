package ru.tinkoff.kora.json.module.http.client;

import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.io.IOException;

public class JsonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final JsonWriter<T> jsonWriter;

    public JsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    @Override
    public HttpClientRequestBuilder apply(Request<T> request) {
        try {
            var bytes = this.jsonWriter.toByteArray(request.parameter());
            return request.builder().body(bytes)
                .header("content-type", "application/json");
        } catch (IOException e) {
            throw new HttpClientEncoderException(e);
        }
    }
}
