package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

public record SimpleRetrierConfig(@Nullable Map<String, NamedConfig> retryable) {

    public static final String DEFAULT = "default";

    public NamedConfig getNamedConfig(@Nonnull String name) {
        if (retryable == null)
            throw new IllegalStateException("Retryable no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig defaultConfig = retryable.get(DEFAULT);
        final NamedConfig namedConfig = retryable.getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("Retryable no configuration is provided, but either '" + name + "' or '" + DEFAULT + "' config is required");

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.delay == null)
            throw new IllegalArgumentException("Retryable 'delay' is not configured in either '" + name + "' or '" + DEFAULT + "' config");
        if (mergedConfig.attempts == null)
            throw new IllegalArgumentException("Retryable 'attempts' is not configured in either '" + name + "' or '" + DEFAULT + "' config");

        if (mergedConfig.attempts < 1)
            throw new IllegalArgumentException("Retryable '" + name + "' attempts can't be less 1, but was " + mergedConfig.attempts);

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new NamedConfig(
            namedConfig.delay == null ? defaultConfig.delay : namedConfig.delay,
            namedConfig.delayStep == null ? defaultConfig.delayStep : namedConfig.delayStep,
            namedConfig.attempts == null ? defaultConfig.attempts : namedConfig.attempts,
            namedConfig.failurePredicateName == null ? defaultConfig.failurePredicateName : namedConfig.failurePredicateName);
    }

    /**
     * {@link #delay} Attempt initial delay
     * {@link #delayStep} Delay step used to calculate next delay (previous delay + delay step)
     * {@link #attempts} Maximum number of retry attempts
     * {@link #failurePredicateName} {@link RetrierFailurePredicate#name()} default is {@link RetrierFailurePredicate}
     */
    public record NamedConfig(@Nullable Duration delay,
                              @Nullable Duration delayStep,
                              @Nullable Integer attempts,
                              @Nullable String failurePredicateName) {

        public NamedConfig(@Nullable Duration delay, @Nullable Duration delayStep, @Nullable Integer attempts, @Nullable String failurePredicateName) {
            this.attempts = attempts;
            this.delay = delay;
            this.delayStep = (delayStep == null) ? Duration.ZERO : delayStep;
            this.failurePredicateName = (failurePredicateName == null) ? SimpleRetrierFailurePredicate.class.getCanonicalName() : failurePredicateName;
        }
    }
}
