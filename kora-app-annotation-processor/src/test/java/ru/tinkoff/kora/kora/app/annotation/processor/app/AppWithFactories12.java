package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithFactories12 {
    default MockLifecycle mock1(ConfigValueExtractor<TestEnum> object) {
        return new MockLifecycle() {};
    }

    default <T extends Enum<T>> EnumConfigValueExtractor<T> enumConfigValueExtractor() {
        return new EnumConfigValueExtractor<>();
    }

    enum TestEnum {}

    interface ConfigValueExtractor<T> {
        T extract(Object value);
    }

    class EnumConfigValueExtractor<T extends Enum<T>> implements ConfigValueExtractor<T> {
        @Override
        public T extract(Object value) {
            return null;
        }
    }

}
