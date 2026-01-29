package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;

@KoraApp
public interface TestConfigApplication extends HoconConfigModule {

    default TestComponent3 testComponent3() {
        return new TestComponent3();
    }

    @Root
    default Object root(Config config) {
        return config;
    }
}
