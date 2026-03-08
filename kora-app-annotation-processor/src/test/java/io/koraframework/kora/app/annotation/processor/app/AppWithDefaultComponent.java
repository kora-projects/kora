package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithDefaultComponent {
    @Root
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

    record Class1(Integer value) {}


    @io.koraframework.common.Module
    interface Module {
        default Integer value2() {
            return 2;
        }
    }
}
