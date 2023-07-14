package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

@Component
@Root
public final class TestComponent23 {

    private final TestComponent2 lifecycleComponent;

    public TestComponent23(@Tag(LifecycleComponent.class) TestComponent2 lifecycleComponent) {
        this.lifecycleComponent = lifecycleComponent;
    }

    public String get() {
        return lifecycleComponent.get() + "3";
    }
}
