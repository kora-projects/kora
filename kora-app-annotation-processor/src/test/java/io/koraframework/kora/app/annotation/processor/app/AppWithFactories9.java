package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithFactories9 {
    @Root
    default Object mock1(GenericInterface<String> object) {
        return new Object();
    }

    default Object mock2(GenericImpl<String> object) {
        return new Object();
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
