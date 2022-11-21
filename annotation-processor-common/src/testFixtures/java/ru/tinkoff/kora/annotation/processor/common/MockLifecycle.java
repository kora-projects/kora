package ru.tinkoff.kora.annotation.processor.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

public interface MockLifecycle extends Lifecycle {
    @Override
    default Mono<Void> init() {
        return Mono.empty();
    }

    @Override
    default Mono<Void> release() {
        return Mono.empty();
    }

    static MockLifecycle empty() {
        return new MockLifecycle() {};
    }
}
