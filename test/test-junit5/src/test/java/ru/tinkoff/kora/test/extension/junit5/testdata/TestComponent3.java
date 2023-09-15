package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

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
