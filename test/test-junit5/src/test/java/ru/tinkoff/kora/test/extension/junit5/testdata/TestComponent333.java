package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

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
