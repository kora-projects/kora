package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.http.server.common.mapper.JsonWriterHttpServerResponseMapper;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpServerResponseMapperModule {

    @DefaultComponent
    default HttpServerResponseMapper<HttpServerResponse> noopResponseMapper() {
        return (request, r) -> r;
    }

    @DefaultComponent
    default HttpServerResponseMapper<ByteBuffer> byteBufBodyResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    @DefaultComponent
    default HttpServerResponseMapper<byte[]> byteArrayResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    @DefaultComponent
    default HttpServerResponseMapper<String> stringResponseMapper() {
        return (request, r) -> HttpServerResponse.of(200, HttpBody.plaintext(r));
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> httpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonWriterHttpServerResponseMapper<T> jsonWriterHttpServerResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }
}
