package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.annotation.ApplicationConfig;
import ru.tinkoff.kora.config.common.annotation.Environment;
import ru.tinkoff.kora.config.common.annotation.SystemProperties;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.config.common.factory.MergeConfigFactory;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

import javax.annotation.Nullable;
import java.util.Optional;

public interface CommonConfigModule extends DefaultConfigExtractorsModule {
    @Environment
    default Config environmentConfig() {
        return MapConfigFactory.fromMap("System environment", System.getenv());
    }

    @SystemProperties
    default Config systemProperties() {
        return MapConfigFactory.fromProperties("System properties", System.getProperties());
    }

    default Config config(@Environment Config environment, @Environment Config systemProperties, @Nullable @ApplicationConfig Config applicationConfig) {
        var config = MergeConfigFactory.merge(environment, systemProperties);
        if (applicationConfig != null) {
            config = MergeConfigFactory.merge(config, applicationConfig);
        }
        return config.resolve();
    }

    @Root
    default ConfigWatcher configRefresher(@ApplicationConfig Optional<ValueOf<ConfigOrigin>> applicationConfig) {
        return new ConfigWatcher(applicationConfig, 1000);
    }
}
