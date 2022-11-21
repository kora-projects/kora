package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

import java.io.Closeable;

@KoraApp
public interface AppWithFactories10 {
    default MockLifecycle mock1(Closeable object) {
        return new MockLifecycle() {};
    }
}
