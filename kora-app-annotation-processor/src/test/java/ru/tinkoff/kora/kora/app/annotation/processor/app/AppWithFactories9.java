package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithFactories9 {
    default MockLifecycle mock1(GenericInterface<String> object) {
        return new MockLifecycle() {};
    }

    default MockLifecycle mock2(GenericImpl<String> object) {
        return new MockLifecycle() {};
    }

    default <T> GenericInterface<T> factory1(TypeRef<T> t) {
        return new GenericImpl<>();
    }

    default <T> GenericImpl<T> factory2(TypeRef<T> t) {
        return new GenericImpl<>();
    }

    interface GenericInterface<T> {}

    class GenericImpl<T> implements GenericInterface<T> {}
}
