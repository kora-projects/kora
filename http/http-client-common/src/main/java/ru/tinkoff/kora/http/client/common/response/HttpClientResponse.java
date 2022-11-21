package ru.tinkoff.kora.http.client.common.response;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.nio.ByteBuffer;

public interface HttpClientResponse {
    int code();

    HttpHeaders headers();

    Flux<ByteBuffer> body();

    Mono<Void> close();

    record Default(int code, HttpHeaders headers, Flux<ByteBuffer> body, Mono<Void> close) implements HttpClientResponse {}
}
