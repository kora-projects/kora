package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

import java.util.function.Function;

@KoraApp
public interface TestApplication extends TestExtendModule {

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
    default Wrapped<Integer> wrappedInt() {
        return new LifecycleWrapper<>(1, (i) -> {}, (i) -> {});
    }

    @Root
    default Wrapped<SomeWrapped> someWrapped() {
        return new LifecycleWrapper<>(new SomeWrapped() {}, s -> {}, s -> {});
    }

    @Root
    default SomeContainer someContainer(SomeWrapped wrapped) {
        return () -> wrapped;
    }

    @Root
    default Wrapped<SomeChild> wrappedSomeChild() {
        return new LifecycleWrapper<>(new SomeChild() {}, (i) -> {}, (i) -> {});
    }

    @Root
    default CustomWrapper floatWrapper() {
        return new CustomWrapper();
    }

    @Root
    default Function<String, Integer> consumerExample() {
        return (s) -> 1;
    }

    class CustomWrapper implements Wrapped<Float> {

        @Override
        public Float value() {
            return 1.0F;
        }
    }

    interface SomeWrapped {

    }

    interface SomeContainer {

        SomeWrapped wrapped();
    }

    interface SomeParent {

    }

    interface SomeChild extends SomeParent {

    }
}
