package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithFactories13 {

    @Root
    default Object target(ConfigValueMapper<TestEnum> object) {
        return new Object();
    }

    default <T extends Enum<T> & MarkerOne> ConfigValueMapper<T> extractor1(TypeRef<T> ref) {
        return value -> null;
    }

    default <T extends Enum<T> & MarkerTwo> ConfigValueMapper<T> extractor2(TypeRef<T> ref) {
        return value -> null;
    }

    enum TestEnum implements MarkerOne {}

    interface MarkerOne { }

    interface MarkerTwo { }

    interface ConfigValueMapper<T> {

        T extract(Object value);
    }
}
