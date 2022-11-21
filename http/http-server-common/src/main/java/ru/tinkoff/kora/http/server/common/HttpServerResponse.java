package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.nio.ByteBuffer;

public interface HttpServerResponse {
    int code();

    int contentLength();

    String contentType();

    HttpHeaders headers();

    Flux<? extends ByteBuffer> body();
}
