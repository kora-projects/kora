package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

public interface Lifecycle {
    Mono<?> init();

    Mono<?> release();
}
