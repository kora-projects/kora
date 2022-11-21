package ru.tinkoff.kora.kora.app.annotation.processor.app;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithLazyComponents {
    default Wrapped<WrappedClass> wrappedClass() {
        return WrappedClass::new;
    }

    default Class0 class0() {
        return new Class0();
    }

    default Class1 class1(Class0 class0) {
        return new Class1();
    }

    default Class2 class2(Class1 class1) {
        return new Class2();
    }

    default Class3 class3(Class2 class2) {
        throw new RuntimeException();
    }

    class Class0 {}

    class Class1 {}

    class Class2 implements Lifecycle {
        @Override
        public Mono<Void> init() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> release() {
            return Mono.empty();
        }
    }

    class Class3 {}

    class WrappedClass implements MockLifecycle {}
}
