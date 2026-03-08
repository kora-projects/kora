package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.Tag;

@Tag(LifecycleComponent.class)
@Component
public class TestComponent2 {

    public String get() {
        return "2";
    }
}
