package ru.tinkoff.kora.resilient.timeout.simple;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

public record SimpleTimeoutConfig(@Nullable Map<String, NamedConfig> timeout) {

    public static final String DEFAULT = "default";

    public NamedConfig getNamedConfig(@Nonnull String name) {
        if (timeout == null)
            throw new IllegalStateException("Timeout no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig defaultConfig = timeout.get(DEFAULT);
        final NamedConfig namedConfig = timeout.getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("Timeout no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.duration == null)
            throw new IllegalStateException("Timeout 'duration' is not configured in either '" + name + "' or '" + DEFAULT + "' config");

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new NamedConfig(namedConfig.duration == null ? defaultConfig.duration : namedConfig.duration);
    }

    /**
     * {@link #duration} Configures maximum interval for timeout.
     */
    public record NamedConfig(Duration duration) {}
}
