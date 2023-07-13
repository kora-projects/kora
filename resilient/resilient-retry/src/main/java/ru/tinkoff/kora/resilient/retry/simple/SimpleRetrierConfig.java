package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@ConfigValueExtractor
public interface SimpleRetrierConfig {
    String DEFAULT = "default";

    default Map<String, NamedConfig> retry() {
        return Map.of();
    }

    default NamedConfig getNamedConfig(@Nonnull String name) {
        if (retry() == null)
            throw new IllegalStateException("Retryable no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig defaultConfig = retry().get(DEFAULT);
        final NamedConfig namedConfig = retry().getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("Retryable no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.delay() == null)
            throw new IllegalArgumentException("Retryable 'delay' is not configured in either '" + name + "' or '" + DEFAULT + "' config");
        if (mergedConfig.attempts() == null)
            throw new IllegalArgumentException("Retryable 'attempts' is not configured in either '" + name + "' or '" + DEFAULT + "' config");

        if (mergedConfig.attempts() < 1)
            throw new IllegalArgumentException("Retryable '" + name + "' attempts can't be less 1, but was " + mergedConfig.attempts());

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $SimpleRetrierConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.delay() == null ? defaultConfig.delay() : namedConfig.delay(),
            namedConfig.delayStep() == null ? Objects.requireNonNullElse(defaultConfig.delayStep(), Duration.ZERO) : namedConfig.delayStep(),
            namedConfig.attempts() == null ? defaultConfig.attempts() : namedConfig.attempts(),
            namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }

    /**
     * {@link #delay} Attempt initial delay
     * {@link #delayStep} Delay step used to calculate next delay (previous delay + delay step)
     * {@link #attempts} Maximum number of retry attempts
     * {@link #failurePredicateName} {@link RetrierFailurePredicate#name()} default is {@link RetrierFailurePredicate}
     */
    @ConfigValueExtractor
    interface NamedConfig {
        @Nullable
        Duration delay();

        @Nullable
        Duration delayStep();

        @Nullable
        Integer attempts();

        default String failurePredicateName() {
            return SimpleRetrierFailurePredicate.class.getCanonicalName();
        }
    }
}
