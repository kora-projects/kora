package io.koraframework.http.client.common.response;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.client.common.response.mapper.JsonHttpClientResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpClientResponseMapperModule {

    @DefaultComponent
    default HttpClientResponseMapper<String> stringHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    @DefaultComponent
    default HttpClientResponseMapper<byte[]> byteArrayHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return body.asInputStream().readAllBytes();
            }
        };
    }

    @DefaultComponent
    default HttpClientResponseMapper<ByteBuffer> byteBufferHttpClientResponseMapper() {
        return response -> {
            try (var body = response.body()) {
                return ByteBuffer.wrap(body.asInputStream().readAllBytes());
            }
        };
    }

    @DefaultComponent
    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> entityResponseHttpClientResponseMapper(HttpClientResponseMapper<T> mapper) {
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), mapper.apply(response));
    }

    @DefaultComponent
    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> jsonEntityResponseHttpClientResponseMapper(JsonReader<T> reader) {
        var delegate = new JsonHttpClientResponseMapper<>(reader);
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), delegate.apply(response));
    }

    @Tag(Json.class)
    @DefaultComponent
    default <T> JsonHttpClientResponseMapper<T> jsonHttpClientResponseMapper(JsonReader<T> reader) {
        return new JsonHttpClientResponseMapper<>(reader);
    }
}
