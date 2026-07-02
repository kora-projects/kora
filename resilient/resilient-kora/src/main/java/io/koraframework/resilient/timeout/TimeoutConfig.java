package io.koraframework.resilient.timeout;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetryConfig;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@ConfigMapper
public interface TimeoutConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> timeout() {
        return Map.of();
    }

    TimeoutTelemetryConfig telemetry();

    /**
     * {@link #duration} Configures maximum interval for timeout.
     */
    @ConfigMapper
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        Duration duration();
    }

    default NamedConfig getNamedConfig(String name) {
        Objects.requireNonNull(name);
        if (timeout() == null)
            throw new IllegalStateException("Timeout no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig defaultConfig = timeout().get(DEFAULT);
        final NamedConfig namedConfig = timeout().getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("Timeout no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.duration() == null)
            throw new IllegalStateException("Timeout 'duration' is not configured in either '" + name + "' or '" + DEFAULT + "' config");

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $TimeoutConfig_NamedConfig_ConfigValueMapper.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.duration() == null ? defaultConfig.duration() : namedConfig.duration());
    }
}
