package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface TestApplication {
    @Root
    default GenericComponent<String> genericComponent1() {
        return new GenericComponent.StringGenericComponent();
    }

    @Root
    default GenericComponent<Integer> genericComponent2() {
        return new GenericComponent.IntGenericComponent();
    }

    default LifecycleComponent lifecycleComponent1() {
        return () -> "1";
    }
}
