package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.annotation.Root;

@Root
@Component
public class TestComponent333 implements LifecycleComponent {

    private final TestComponent33 component33;

    public TestComponent333(TestComponent33 component33) {
        this.component33 = component33;
    }

    public String get() {
        return component33.get() + "3";
    }
}
