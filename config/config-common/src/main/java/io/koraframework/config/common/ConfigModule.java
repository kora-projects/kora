package io.koraframework.config.common;

import io.koraframework.application.graph.Node;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.config.common.annotation.ApplicationConfig;
import io.koraframework.config.common.annotation.EnvironmentConfig;
import io.koraframework.config.common.annotation.SystemPropertiesConfig;
import io.koraframework.config.common.mapper.ConfigValueMapperModule;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.config.common.util.ConfigMergingUtils;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.EnvironmentOrigin;
import io.koraframework.config.common.origin.SystemPropertiesOrigin;
import org.jspecify.annotations.Nullable;

public interface ConfigModule extends ConfigValueMapperModule {

    @EnvironmentConfig
    default Config environmentConfig() {
        return ConfigMappingUtils.fromMap(new EnvironmentOrigin("Environment Variables"), System.getenv());
    }

    @SystemPropertiesConfig
    default Config systemPropertiesConfig() {
        return ConfigMappingUtils.fromProperties(new SystemPropertiesOrigin("System Properties"), System.getProperties());
    }

    default Config config(@EnvironmentConfig Config environment, @SystemPropertiesConfig Config systemProperties, @Nullable @ApplicationConfig Config applicationConfig) {
        var config = ConfigMergingUtils.merge(environment, systemProperties);
        if (applicationConfig != null) {
            config = ConfigMergingUtils.merge(config, applicationConfig);
        }
        return config.resolve();
    }

    @Root
    @DefaultComponent
    default ConfigWatcher applicationConfigWatcher(RefreshableGraph graph, @Nullable @ApplicationConfig Node<? extends ConfigOrigin> applicationConfigNode) {
        return new ConfigWatcher(graph, applicationConfigNode, 1000);
    }
}
