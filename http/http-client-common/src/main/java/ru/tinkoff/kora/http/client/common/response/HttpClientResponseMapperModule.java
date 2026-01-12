package ru.tinkoff.kora.http.client.common.response;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.response.mapper.JsonHttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpClientResponseMapperModule {

    default HttpClientResponseMapper<String> stringHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    default HttpClientResponseMapper<byte[]> byteArrayHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return body.asInputStream().readAllBytes();
            }
        };
    }

    default HttpClientResponseMapper<ByteBuffer> byteBufferHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return ByteBuffer.wrap(body.asInputStream().readAllBytes());
            }
        };
    }

    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> entityResponseHttpClientResponseMapper(HttpClientResponseMapper<T> mapper) {
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), mapper.apply(response));
    }

    @Tag(Json.class)
    default <T> JsonHttpClientResponseMapper<T> jsonHttpClientResponseMapper(JsonReader<T> reader) {
        return new JsonHttpClientResponseMapper<>(reader);
    }
}
