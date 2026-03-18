package io.koraframework.http.client.common.request.mapper;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {

    @DefaultComponent
    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<String> stringHttpClientRequestMapper() {
        return (body) -> HttpBody.plaintext(body);
    }

    @DefaultComponent
    default FormUrlEncodedClientRequestMapper formUrlEncodedHttpClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    @DefaultComponent
    default FormMultipartClientRequestMapper formMultipartHttpClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }

    @Tag(Json.class)
    @DefaultComponent
    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> writer) {
        return new JsonHttpClientRequestMapper<>(writer);
    }
}
