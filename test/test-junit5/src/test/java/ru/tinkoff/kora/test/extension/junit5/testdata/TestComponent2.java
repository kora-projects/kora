package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Tag;

@Tag(LifecycleComponent.class)
@Component
public class TestComponent2 {

    public String get() {
        return "2";
    }
}
