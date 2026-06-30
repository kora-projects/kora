package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.annotation.Component;
import io.koraframework.common.annotation.Tag;

@Tag(LifecycleComponent.class)
@Component
public class TestComponent2 {

    public String get() {
        return "2";
    }
}
