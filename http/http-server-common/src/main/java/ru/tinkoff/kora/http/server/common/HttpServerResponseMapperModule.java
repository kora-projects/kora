package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import java.nio.ByteBuffer;

public interface HttpServerResponseMapperModule {

    default HttpServerResponseMapper<HttpServerResponse> noopResponseMapper() {
        return (ctx, request, r) -> r;
    }

    default HttpServerResponseMapper<ByteBuffer> byteBufBodyResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    default HttpServerResponseMapper<byte[]> byteArrayResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    default HttpServerResponseMapper<String> stringResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.plaintext(r));
    }

    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> httpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }
}
