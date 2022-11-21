package ru.tinkoff.kora.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface KoraApp {
}



interface TestApp {
    default Lifecycle someLifecycle() {
        return new Lifecycle() {
            @Override
            public Mono<?> init() {
                return null;
            }

            @Override
            public Mono<?> release() {
                return null;
            }
        };
    }
}
