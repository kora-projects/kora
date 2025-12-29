package ru.tinkoff.kora.resilient.retry;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@ConfigValueExtractor
public interface RetryConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> retry() {
        return Map.of();
    }

    /**
     * {@link #delay} Attempt initial delay
     * {@link #delayStep} Delay step used to calculate next delay (previous delay + delay step)
     * {@link #attempts} Maximum number of retry attempts
     * {@link #failurePredicateName} {@link RetryPredicate#name()} default is {@link RetryPredicate}
     */
    @ConfigValueExtractor
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        Duration delay();

        @Nullable
        Duration delayStep();

        @Nullable
        Integer attempts();

        default String failurePredicateName() {
            return KoraRetryPredicate.class.getCanonicalName();
        }
    }

    default NamedConfig getNamedConfig(String name) {
        if (retry() == null)
            throw new IllegalStateException("Retry no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig defaultConfig = retry().get(DEFAULT);
        final NamedConfig namedConfig = retry().getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("Retry no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.delay() == null)
            throw new IllegalArgumentException("Retry 'delay' is not configured in either '" + name + "' or '" + DEFAULT + "' config");
        if (mergedConfig.attempts() == null)
            throw new IllegalArgumentException("Retry 'attempts' is not configured in either '" + name + "' or '" + DEFAULT + "' config");

        if (mergedConfig.attempts() < 0)
            throw new IllegalArgumentException("Retry '" + name + "' attempts can't be less 0, but was " + mergedConfig.attempts());

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            if (namedConfig.delayStep() == null) {
                return new $RetryConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
                    namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : true,
                    namedConfig.delay(),
                    Duration.ZERO,
                    namedConfig.attempts(),
                    namedConfig.failurePredicateName());
            }

            return namedConfig;
        }

        return new $RetryConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.delay() == null ? defaultConfig.delay() : namedConfig.delay(),
            namedConfig.delayStep() == null ? Objects.requireNonNullElse(defaultConfig.delayStep(), Duration.ZERO) : namedConfig.delayStep(),
            namedConfig.attempts() == null ? defaultConfig.attempts() : namedConfig.attempts(),
            namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }
}
