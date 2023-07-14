package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

@KoraApp
public interface AppWithFactories12 {
    @Root
    default Object mock1(ConfigValueExtractor<TestEnum> object) {
        return new Object();
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
