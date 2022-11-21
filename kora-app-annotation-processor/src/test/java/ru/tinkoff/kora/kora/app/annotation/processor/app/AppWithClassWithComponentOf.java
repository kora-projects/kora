package ru.tinkoff.kora.kora.app.annotation.processor.app;

import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithClassWithComponentOf {
    default MockLifecycle object1(Class1 class1) {
        return Mockito.spy(MockLifecycle.class);
    }

    default MockLifecycle object2(ValueOf<Class3> class1) {
        return Mockito.spy(MockLifecycle.class);
    }

    @Component
    class Class1 implements Lifecycle {
        private final Class2 class2;

        public Class1(Class2 class2) {
            this.class2 = class2;
        }

        @Override
        public Mono<Void> init() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> release() {
            return Mono.empty();
        }
    }

    @Component
    class Class2 {}
    @Component
    class Class3 {}
}
