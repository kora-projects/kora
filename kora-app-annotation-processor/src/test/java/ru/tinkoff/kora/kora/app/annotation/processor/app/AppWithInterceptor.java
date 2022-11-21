package ru.tinkoff.kora.kora.app.annotation.processor.app;

import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithInterceptor {


    default Class1 class1() {
        return new Class1();
    }

    default Interceptor interceptor() {
        return new Interceptor();
    }

    default MockLifecycle lifecycle(Class1 class1) {
        return Mockito.spy(MockLifecycle.class);
    }


    class Class1 {}

    class Interceptor implements GraphInterceptor<Class1> {

        @Override
        public Mono<Class1> init(Class1 value) {
            return Mono.just(value);
        }

        @Override
        public Mono<Class1> release(Class1 value) {
            return Mono.just(value);
        }
    }

}
