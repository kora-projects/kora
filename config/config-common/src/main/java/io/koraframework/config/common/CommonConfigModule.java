package io.koraframework.config.common;

import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.annotation.ApplicationConfig;
import io.koraframework.config.common.annotation.Environment;
import io.koraframework.config.common.annotation.SystemProperties;
import io.koraframework.config.common.factory.MapConfigFactory;
import io.koraframework.config.common.factory.MergeConfigFactory;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.EnvironmentOrigin;
import io.koraframework.config.common.origin.SystemPropertiesOrigin;

import java.util.Optional;

public interface CommonConfigModule extends DefaultConfigExtractorsModule {

    @Environment
    default Config environmentConfig() {
        return MapConfigFactory.fromMap(new EnvironmentOrigin("Environment Variables"), System.getenv());
    }

    @SystemProperties
    default Config systemProperties() {
        return MapConfigFactory.fromProperties(new SystemPropertiesOrigin("System Properties"), System.getProperties());
    }

    default Config config(@Environment Config environment, @SystemProperties Config systemProperties, @Nullable @ApplicationConfig Config applicationConfig) {
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
