package ru.tinkoff.kora.kora.app.annotation.processor.app;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.KoraApp;

import java.time.Duration;
import java.util.Objects;

@KoraApp
public interface AppWithAllOfValueOf {
    default Class1 class1(All<ValueOf<Class2>> cls) {
        for (var cl : cls) {
            Objects.requireNonNull(cl.get());
        }
        return new Class1();
    }

    default Class2 class2() {
        return new Class2();
    }

    class Class1 implements MockLifecycle {}

    class Class2 implements MockLifecycle {
        @Override
        public Mono<Void> init() {
            return Mono.delay(Duration.ofSeconds(1)).then();
        }
    }

}
