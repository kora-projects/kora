package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

import java.io.Closeable;

@KoraApp
public interface AppWithFactories10 {
    @Root
    default Object mock1(Closeable object) {
        return new Object();
    }
}
