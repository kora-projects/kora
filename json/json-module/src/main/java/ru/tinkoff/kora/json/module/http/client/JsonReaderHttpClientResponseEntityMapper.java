package ru.tinkoff.kora.json.module.http.client;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public final class JsonReaderHttpClientResponseEntityMapper<T> implements HttpClientResponseMapper<HttpResponseEntity<T>> {
    private final JsonReader<T> jsonReader;

    public JsonReaderHttpClientResponseEntityMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public HttpResponseEntity<T> apply(@Nonnull HttpClientResponse response) throws IOException, HttpClientDecoderException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            var value = jsonReader.read(is);
            return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
        }
    }
}
