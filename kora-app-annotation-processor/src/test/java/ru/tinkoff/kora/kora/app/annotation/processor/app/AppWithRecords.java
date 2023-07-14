package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithRecords {
    @Root
    default Object str(TestConfig testConfig) {
        return new Object();
    }

    record TestConfig() {}
}
