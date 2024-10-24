package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithFactories13 {

    @Root
    default Object target(ConfigValueExtractor<TestEnum> object) {
        return new Object();
    }

    default <T extends Enum<T> & MarkerOne> ConfigValueExtractor<T> extractor1(TypeRef<T> ref) {
        return value -> null;
    }

    default <T extends Enum<T> & MarkerTwo> ConfigValueExtractor<T> extractor2(TypeRef<T> ref) {
        return value -> null;
    }

    enum TestEnum implements MarkerOne {}

    interface MarkerOne { }

    interface MarkerTwo { }

    interface ConfigValueExtractor<T> {

        T extract(Object value);
    }
}
