package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.HttpHeaders;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public record SimpleHttpServerResponse(int code, String contentType, HttpHeaders headers, int contentLength, Flux<? extends ByteBuffer> body) implements HttpServerResponse {
    public SimpleHttpServerResponse(int code, String contentType, HttpHeaders headers, @Nullable ByteBuffer bodyBuffer) {
        this(
            code,
            contentType,
            headers,
            bodyBuffer == null ? 0 : bodyBuffer.remaining(),
            bodyBuffer == null ? Flux.empty() : Mono.fromCallable(bodyBuffer::slice).flux()
        );
    }
}
