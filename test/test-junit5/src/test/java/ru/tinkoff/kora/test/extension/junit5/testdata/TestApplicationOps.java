package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface TestApplicationOps {

    default TestComponent3 testComponent3() {
        return new TestComponent3() {
            @Override
            public void init() {
                throw new IllegalStateException("OPS");
            }
        };
    }
}
