package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithRecords {
    @Root
    default Object str(TestConfig testConfig) {
        return new Object();
    }

    record TestConfig() {}
}
