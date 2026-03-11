package io.koraframework.http.client.common.response.mapper;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.json.common.JsonReader;

import java.io.IOException;

public class JsonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T> {
    private final JsonReader<T> jsonReader;

    public JsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public T apply(HttpClientResponse response) throws IOException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            return this.jsonReader.read(is);
        }
    }
}
