package io.koraframework.http.client.common.response.mapper;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.json.common.JsonReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PushbackInputStream;

public class JsonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T> {
    private final JsonReader<T> jsonReader;

    public JsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    @Nullable
    public T apply(HttpClientResponse response) throws IOException {
        try (var body = response.body();
             var is = new PushbackInputStream(body.asInputStream(), 1)) {
            int b;
            do {
                b = is.read();
            } while (b == ' ' || b == '\t' || b == '\n' || b == '\r');
            if (b < 0) {
                // empty or whitespace-only body
                return null;
            }
            is.unread(b);
            return this.jsonReader.read(is);
        }
    }
}
