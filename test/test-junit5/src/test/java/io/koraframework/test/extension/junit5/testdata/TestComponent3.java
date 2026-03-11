package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.annotation.Root;

@Root
@Component
public class TestComponent3 implements LifecycleComponent {

    public String get() {
        return "3";
    }

    @Override
    public void init() {
        throw new IllegalStateException("OPS");
    }
}
