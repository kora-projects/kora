package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithRecords {
    default MockLifecycle str(TestConfig testConfig) {
        return new MockLifecycle() {};
    }

    record TestConfig() {}
}
