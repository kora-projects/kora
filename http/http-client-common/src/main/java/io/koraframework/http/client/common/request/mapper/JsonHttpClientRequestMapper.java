package io.koraframework.http.client.common.request.mapper;

import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.body.JsonHttpBodyOutput;
import io.koraframework.json.common.JsonWriter;

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
