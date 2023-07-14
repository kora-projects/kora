package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Component
@Root
public class TestComponent12 implements LifecycleComponent {

    private final TestComponent1 component1;

    public TestComponent12(TestComponent1 component1) {
        this.component1 = component1;
    }

    public String get() {
        return component1.get() + "2";
    }
}
