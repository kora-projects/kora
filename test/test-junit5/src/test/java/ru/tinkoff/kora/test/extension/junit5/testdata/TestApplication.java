package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

import java.util.function.Function;
import java.util.function.Supplier;

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
    default ComplexHolder complexWrap() {
        return new ComplexHolder();
    }

    @Root
    @Tag(ComplexInterfaceHolder.class)
    default ComplexInterfaceHolder<String> complexInterfaceStringWrap() {
        return new ComplexInterfaceHolder<>() {

            @Override
            public void init() {}

            @Override
            public void release() {}

            @Override
            public String generic() {
                return "1";
            }

            @Override
            public ComplexWrapped value() {
                return () -> "1";
            }

            @Override
            public String other() {
                return "1";
            }
        };
    }

    @Tag(ComplexHolder.class)
    @Root
    default String complexHolderDependency(ComplexHolder holder) {
        return "holder-" + holder.value().wrapped() + "-" + holder.other();
    }

    @Tag(ComplexWrapped.class)
    @Root
    default String complexWrappedDependency(ComplexWrapped wrap) {
        return "wrapped-" + wrap.wrapped();
    }

    @Tag(ComplexOther.class)
    @Root
    default String complexOtherDependency(ComplexOther other) {
        return "other-" + other.other();
    }

    @Root
    default Function<String, Integer> consumerExample() {
        return (s) -> 1;
    }

    @Root
    default Function<Supplier<String>, Supplier<Integer>> consumerMegaExample() {
        return (s) -> () -> 1;
    }

    class CustomWrapper implements Wrapped<SomeContract> {

        @Override
        public SomeContract value() {
            return () -> "1";
        }
    }

    class ComplexHolder implements Wrapped<ComplexWrapped>, Lifecycle, ComplexOther {

        @Override
        public void init() {}

        @Override
        public void release() {}

        @Override
        public ComplexWrapped value() {
            return () -> "1";
        }

        @Override
        public String other() {
            return "1";
        }
    }

    interface ComplexInterfaceHolder<T> extends Wrapped<ComplexWrapped>, Lifecycle, ComplexOther {

        T generic();
    }

    interface ComplexWrapped {
        String wrapped();
    }

    interface ComplexOther {
        String other();
    }

    interface SomeContract {
        String get();
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
