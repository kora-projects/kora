package ru.tinkoff.kora.http.client.common.response;

import ru.tinkoff.kora.http.common.HttpResponseEntity;

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
}
