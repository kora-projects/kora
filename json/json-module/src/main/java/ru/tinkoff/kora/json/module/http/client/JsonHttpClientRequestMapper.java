package ru.tinkoff.kora.json.module.http.client;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.http.JsonHttpBodyOutput;

public class JsonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final JsonWriter<T> jsonWriter;

    public JsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        this.jsonWriter = jsonWriter;
    }


    @Override
    public HttpClientRequestBuilder apply(Context ctx, HttpClientRequestBuilder builder, T value) {
        return builder.body(new JsonHttpBodyOutput<>(this.jsonWriter, ctx, value));
    }
}
