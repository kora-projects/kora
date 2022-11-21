package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Mono;

public interface HttpServerResponseSender {
    interface SendResult {}

    record Success(int code) implements SendResult {}

    record ResponseBodyErrorBeforeCommit(Throwable error) implements SendResult {}

    record ResponseBodyError(Throwable error) implements SendResult {}

    record ConnectionError(Throwable error) implements SendResult {}

    Mono<SendResult> send(HttpServerResponse response);
}

