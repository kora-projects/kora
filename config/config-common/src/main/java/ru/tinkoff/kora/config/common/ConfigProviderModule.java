package ru.tinkoff.kora.config.common;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.application.graph.ValueOf;

public interface ConfigProviderModule {
    default Config config() {
        ConfigFactory.invalidateCaches();
        return ConfigFactory.load();
    }

    default ConfigWatcher configRefresher(ValueOf<Config> applicationConfig) {
        return new ConfigWatcher(applicationConfig, 1000);
    }
}
