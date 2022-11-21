package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithFactories11 {
    default MockLifecycle mock1(GenericClass<String> object) {
        return new MockLifecycle() {};
    }

    default <T> GenericClass<T> factory1(java.io.Closeable t) {
        return new GenericClass<>();
    }

    default <T> GenericClass<T> factory2(Long t) {
        return new GenericClass<>();
    }

    class GenericClass<T> {}
}
