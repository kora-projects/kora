package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@ConfigValueExtractor
public interface TimeoutConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> timeout() {
        return Map.of();
    }

    /**
     * {@link #duration} Configures maximum interval for timeout.
     */
    @ConfigValueExtractor
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        Duration duration();
    }

    default NamedConfig getNamedConfig(@Nonnull String name) {
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

        return new $TimeoutConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.duration() == null ? defaultConfig.duration() : namedConfig.duration());
    }
}
