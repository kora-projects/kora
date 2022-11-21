package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

public interface GraphInterceptor<T> {
    Mono<T> init(T value);

    Mono<T> release(T value);
}
