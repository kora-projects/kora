package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithDefaultComponent {
    default Class1 class1(Integer value, Long value1) {
        return new Class1(value);
    }

    @DefaultComponent
    default Integer value1() {
        return 1;
    }

    @DefaultComponent
    default Long longValue() {
        return 1L;
    }

    record Class1(Integer value) implements MockLifecycle {}


    @ru.tinkoff.kora.common.Module
    interface Module {
        default Integer value2() {
            return 2;
        }
    }
}
