package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.application.graph.Lifecycle;

public interface LifecycleComponent extends Lifecycle {

    String get();

    @Override
    default void init() {
    }

    @Override
    default void release() {
    }
}
