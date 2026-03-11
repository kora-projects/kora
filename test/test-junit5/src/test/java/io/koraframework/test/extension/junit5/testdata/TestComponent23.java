package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;

@Root
@Component
public final class TestComponent23 {

    private final TestComponent2 lifecycleComponent;

    public TestComponent23(@Tag(LifecycleComponent.class) TestComponent2 lifecycleComponent) {
        this.lifecycleComponent = lifecycleComponent;
    }

    public String get() {
        return lifecycleComponent.get() + "3";
    }
}
