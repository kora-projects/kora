package io.koraframework.http.client.common.request.mapper;

import io.koraframework.common.Tag;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {

    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<String> stringHttpClientRequestMapper() {
        return (body) -> HttpBody.plaintext(body);
    }

    default FormUrlEncodedClientRequestMapper formUrlEncodedClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    default FormMultipartClientRequestMapper formMultipartClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }

    @Tag(Json.class)
    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> writer) {
        return new JsonHttpClientRequestMapper<>(writer);
    }
}
