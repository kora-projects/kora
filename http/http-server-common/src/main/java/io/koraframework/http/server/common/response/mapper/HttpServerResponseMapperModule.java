package io.koraframework.http.server.common.response.mapper;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpServerResponseMapperModule {

    @DefaultComponent
    default HttpServerResponseMapper<HttpServerResponse> noopHttpServerResponseMapper() {
        return (request, r) -> r;
    }

    @DefaultComponent
    default HttpServerResponseMapper<ByteBuffer> byteBufBodyHttpServerResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    @DefaultComponent
    default HttpServerResponseMapper<byte[]> byteArrayHttpServerResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    @DefaultComponent
    default HttpServerResponseMapper<String> stringHttpServerResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.plaintext(r));
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> httpResponseEntityHttpServerResponseMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> jsonHttpResponseEntityHttpServerResponseMapper(JsonWriter<T> writer) {
        return new HttpServerResponseEntityMapper<>(new JsonWriterHttpServerResponseMapper<>(writer));
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonWriterHttpServerResponseMapper<T> jsonHttpServerResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }
}
