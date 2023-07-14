package ru.tinkoff.kora.test.extension.junit5.testdata;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

public interface LifecycleComponent extends Lifecycle {

    String get();

    @Override
    default Mono<?> init() {
        return Mono.empty();
    }

    @Override
    default Mono<?> release() {
        return Mono.empty();
    }
}
