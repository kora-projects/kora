package ru.tinkoff.kora.http.client.common.request.mapper;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.body.JsonHttpBodyOutput;
import ru.tinkoff.kora.json.common.JsonWriter;

public class JsonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final JsonWriter<T> jsonWriter;

    public JsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    @Override
    public HttpBodyOutput apply(T value) {
        return new JsonHttpBodyOutput<>(this.jsonWriter, value);
    }
}
