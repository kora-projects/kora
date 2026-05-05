package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.KoraApp;

@KoraApp
public interface TestApplicationOps {

    @DefaultComponent
    default TestComponent3 testComponent3() {
        return new TestComponent3() {
            @Override
            public void init() {
                throw new IllegalStateException("OPS");
            }
        };
    }
}
