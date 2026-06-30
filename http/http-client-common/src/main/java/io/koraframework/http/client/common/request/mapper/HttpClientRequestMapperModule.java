package io.koraframework.http.client.common.request.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {

    @DefaultComponent
    default HttpClientRequestMapper<byte[]> httpClientRequestByteArrayMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<ByteBuffer> httpClientRequestByteBufferMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<String> httpClientRequestStringMapper() {
        return (body) -> HttpBody.plaintext(body);
    }

    @DefaultComponent
    default FormUrlEncodedClientRequestMapper httpClientRequestFormUrlEncodedMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    @DefaultComponent
    default FormMultipartClientRequestMapper httpClientRequestFormMultipartMapper() {
        return new FormMultipartClientRequestMapper();
    }

    @Tag(Json.class)
    @DefaultComponent
    default <T> JsonHttpClientRequestMapper<T> httpClientRequestJsonMapper(JsonWriter<T> writer) {
        return new JsonHttpClientRequestMapper<>(writer);
    }
}
