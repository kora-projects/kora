package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;

@KoraApp
public interface AppWithFactories12 {
    @Root
    default Object mock1(ConfigValueMapper<TestEnum> object) {
        return new Object();
    }

    default <T extends Enum<T>> EnumConfigValueMapper<T> enumConfigValueMapper() {
        return new EnumConfigValueMapper<>();
    }

    enum TestEnum {}

    interface ConfigValueMapper<T> {
        T extract(Object value);
    }

    class EnumConfigValueMapper<T extends Enum<T>> implements ConfigValueMapper<T> {
        @Override
        public T extract(Object value) {
            return null;
        }
    }

}
