package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

import java.io.Closeable;

@KoraApp
public interface AppWithFactories10 {
    @Root
    default Object mock1(Closeable object) {
        return new Object();
    }
}
