package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.application.graph.Lifecycle;

public interface LifecycleComponent extends Lifecycle {

    String get();

    @Override
    default void init() {
    }

    @Override
    default void release() {
    }
}
