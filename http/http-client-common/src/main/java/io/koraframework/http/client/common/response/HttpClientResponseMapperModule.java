package io.koraframework.http.client.common.response;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.client.common.response.mapper.JsonHttpClientResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface HttpClientResponseMapperModule {

    @DefaultComponent
    default HttpClientResponseMapper<String> httpClientResponseStringMapper() {
        return response -> {
            try (var body = response.body()) {
                return new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    @DefaultComponent
    default HttpClientResponseMapper<byte[]> httpClientResponseByteArrayMapper() {
        return response -> {
            try (var body = response.body()) {
                return body.asInputStream().readAllBytes();
            }
        };
    }

    @DefaultComponent
    default HttpClientResponseMapper<ByteBuffer> httpClientResponseByteBufferMapper() {
        return response -> {
            try (var body = response.body()) {
                return ByteBuffer.wrap(body.asInputStream().readAllBytes());
            }
        };
    }

    @DefaultComponent
    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> httpClientResponseEntityResponseMapper(HttpClientResponseMapper<T> mapper) {
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), mapper.apply(response));
    }

    @DefaultComponent
    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> httpClientResponseJsonEntityResponseMapper(JsonReader<T> reader) {
        var delegate = new JsonHttpClientResponseMapper<>(reader);
        return response -> HttpResponseEntity.of(response.code(), response.headers().toMutable(), delegate.apply(response));
    }

    @Json
    @DefaultComponent
    default <T> JsonHttpClientResponseMapper<T> httpClientResponseJsonMapper(JsonReader<T> reader) {
        return new JsonHttpClientResponseMapper<>(reader);
    }
}
