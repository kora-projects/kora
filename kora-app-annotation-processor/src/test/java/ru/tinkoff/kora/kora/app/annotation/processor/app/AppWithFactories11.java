package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithFactories11 {
    @Root
    default Object mock1(GenericClass<String> object) {
        return new Object();
    }

    default <T> GenericClass<T> factory1(java.io.Closeable t) {
        return new GenericClass<>();
    }

    default <T> GenericClass<T> factory2(Long t) {
        return new GenericClass<>();
    }

    class GenericClass<T> {}
}
