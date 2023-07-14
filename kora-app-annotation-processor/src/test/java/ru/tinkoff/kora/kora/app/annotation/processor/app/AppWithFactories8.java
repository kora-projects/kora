package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

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
