package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithFactories8 {
    @Root
    default Object mock(GenericInterface<Integer, String> object) {
        return new Object();
    }

    default <T extends Comparable<T>> GenericImpl<T> impl(TypeRef<T> t) {
        return new GenericImpl<>();
    }


    interface GenericInterface<T1, T2> {}

    class GenericImpl<T> implements GenericInterface<T, String> {}
}
