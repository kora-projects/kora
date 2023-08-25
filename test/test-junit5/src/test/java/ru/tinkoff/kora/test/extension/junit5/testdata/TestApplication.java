package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

import java.util.function.Function;

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

    @Root
    default GenericComponent.LongGenericComponent genericComponent3() {
        return new GenericComponent.LongGenericComponent();
    }

    @Root
    default LifecycleComponent lifecycleComponent1() {
        return () -> "1";
    }

    @Root
    default Function<String, Integer> consumerExample() {
        return (s) -> 1;
    }
}
