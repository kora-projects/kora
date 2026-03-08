package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.annotation.Root;

@Root
@Component
public class TestComponent33 implements LifecycleComponent {

    private final TestComponent3 component3;

    public TestComponent33(TestComponent3 component3) {
        this.component3 = component3;
    }

    public String get() {
        return component3.get() + "3";
    }
}
