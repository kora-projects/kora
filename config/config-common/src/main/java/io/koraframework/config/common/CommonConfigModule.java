package io.koraframework.config.common;

import io.koraframework.application.graph.Node;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.annotation.ApplicationConfig;
import io.koraframework.config.common.annotation.Environment;
import io.koraframework.config.common.annotation.SystemProperties;
import io.koraframework.config.common.factory.MapConfigFactory;
import io.koraframework.config.common.factory.MergeConfigFactory;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.EnvironmentOrigin;
import io.koraframework.config.common.origin.SystemPropertiesOrigin;
import org.jspecify.annotations.Nullable;

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
    default ConfigWatcher configRefresher(RefreshableGraph graph, @Nullable @ApplicationConfig Node<? extends ConfigOrigin> applicationConfigNode, int checkTime) {
        return new ConfigWatcher(graph, applicationConfigNode, 1000);
    }
}
