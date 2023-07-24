package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Root
@Component
public class TestComponent3333 implements LifecycleComponent {

    private final TestComponent333 component333;

    public TestComponent3333(TestComponent333 component333) {
        this.component333 = component333;
    }

    public String get() {
        return component333.get() + "3";
    }
}
