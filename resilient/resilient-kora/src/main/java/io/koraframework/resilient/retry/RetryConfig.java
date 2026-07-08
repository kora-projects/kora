package io.koraframework.resilient.retry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.retry.telemetry.RetryTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@ConfigMapper
public interface RetryConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> retry() {
        return Map.of();
    }

    RetryTelemetryConfig telemetry();

    enum JitterType {
        NONE,
        FULL
    }

    enum BackoffType {
        EXPONENTIAL
    }

    /**
     * {@link #delay} Attempt initial delay
     * {@link #delayStep} Delay step used to calculate next delay (previous delay + delay step)
     * {@link #attempts} Maximum number of retry attempts
     * {@link #failurePredicateName} {@link RetryPredicate#name()} default is {@link RetryPredicate}
     */
    @ConfigMapper
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        Duration delay();

        @Nullable
        Duration delayStep();

        @Nullable
        BackoffConfig backoff();

        @Nullable
        JitterConfig jitter();

        @Nullable
        RetryBudgetConfig retryBudget();

        @Nullable
        Integer attempts();

        default String failurePredicateName() {
            return KoraRetryPredicate.class.getCanonicalName();
        }
    }

    @ConfigMapper
    interface JitterConfig {

        @Nullable
        JitterType type();

        @Nullable
        Double ratio();
    }

    @ConfigMapper
    interface BackoffConfig {

        @Nullable
        BackoffType type();

        @Nullable
        Double multiplier();

        @Nullable
        Duration maxDelay();
    }

    @ConfigMapper
    interface RetryBudgetConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        String name();

        @Nullable
        Double ratio();

        @Nullable
        Integer maxTokens();

        @Nullable
        Integer initialTokens();

        @Nullable
        Double minTokensPerSecond();
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
        if (mergedConfig.backoff() != null) {
            if (mergedConfig.backoff().type() == null)
                throw new IllegalArgumentException("Retry '" + name + "' backoff.type is not configured");
            if (mergedConfig.backoff().multiplier() == null || mergedConfig.backoff().multiplier() <= 0)
                throw new IllegalArgumentException("Retry '" + name + "' backoff.multiplier must be greater than 0, but was " + mergedConfig.backoff().multiplier());
            if (mergedConfig.backoff().maxDelay() != null && mergedConfig.backoff().maxDelay().isNegative())
                throw new IllegalArgumentException("Retry '" + name + "' backoff.maxDelay can't be negative, but was " + mergedConfig.backoff().maxDelay());
        }
        if (mergedConfig.jitter() != null) {
            if (mergedConfig.jitter().ratio() == null || mergedConfig.jitter().ratio() < 0 || mergedConfig.jitter().ratio() > 1)
                throw new IllegalArgumentException("Retry '" + name + "' jitter.ratio must be between 0 and 1, but was " + mergedConfig.jitter().ratio());
        }
        if (mergedConfig.retryBudget() != null && Boolean.TRUE.equals(mergedConfig.retryBudget().enabled())) {
            if (mergedConfig.retryBudget().ratio() == null || mergedConfig.retryBudget().ratio() < 0)
                throw new IllegalArgumentException("Retry '" + name + "' retryBudget.ratio must be non-negative, but was " + mergedConfig.retryBudget().ratio());
            if (mergedConfig.retryBudget().maxTokens() == null || mergedConfig.retryBudget().maxTokens() < 0)
                throw new IllegalArgumentException("Retry '" + name + "' retryBudget.maxTokens can't be less 0, but was " + mergedConfig.retryBudget().maxTokens());
            if (mergedConfig.retryBudget().initialTokens() == null || mergedConfig.retryBudget().initialTokens() < 0)
                throw new IllegalArgumentException("Retry '" + name + "' retryBudget.initialTokens can't be less 0, but was " + mergedConfig.retryBudget().initialTokens());
            if (mergedConfig.retryBudget().initialTokens() > mergedConfig.retryBudget().maxTokens())
                throw new IllegalArgumentException("Retry '" + name + "' retryBudget.initialTokens can't be greater than retryBudget.maxTokens");
            if (mergedConfig.retryBudget().minTokensPerSecond() == null || mergedConfig.retryBudget().minTokensPerSecond() < 0)
                throw new IllegalArgumentException("Retry '" + name + "' retryBudget.minTokensPerSecond can't be less 0, but was " + mergedConfig.retryBudget().minTokensPerSecond());
        }

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, @Nullable NamedConfig defaultConfig) {
        return new $RetryConfig_NamedConfig_ConfigValueMapper.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig == null || defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.delay() == null ? (defaultConfig == null ? null : defaultConfig.delay()) : namedConfig.delay(),
            namedConfig.delayStep() == null ? Objects.requireNonNullElse(defaultConfig == null ? null : defaultConfig.delayStep(), Duration.ZERO) : namedConfig.delayStep(),
            mergeBackoff(namedConfig.backoff(), defaultConfig == null ? null : defaultConfig.backoff()),
            mergeJitter(namedConfig.jitter(), defaultConfig == null ? null : defaultConfig.jitter()),
            mergeRetryBudget(namedConfig.retryBudget(), defaultConfig == null ? null : defaultConfig.retryBudget()),
            namedConfig.attempts() == null ? (defaultConfig == null ? null : defaultConfig.attempts()) : namedConfig.attempts(),
            namedConfig.failurePredicateName() == null ? (defaultConfig == null ? KoraRetryPredicate.class.getCanonicalName() : defaultConfig.failurePredicateName()) : namedConfig.failurePredicateName());
    }

    @Nullable
    private static BackoffConfig mergeBackoff(@Nullable BackoffConfig config, @Nullable BackoffConfig defaultConfig) {
        if (config == null && defaultConfig == null) {
            return null;
        }

        return new $RetryConfig_BackoffConfig_ConfigValueMapper.BackoffConfig_Impl(
            config == null || config.type() == null ? (defaultConfig == null ? null : defaultConfig.type()) : config.type(),
            config == null || config.multiplier() == null ? (defaultConfig == null || defaultConfig.multiplier() == null ? 2.0 : defaultConfig.multiplier()) : config.multiplier(),
            config == null || config.maxDelay() == null ? (defaultConfig == null ? null : defaultConfig.maxDelay()) : config.maxDelay()
        );
    }

    @Nullable
    private static JitterConfig mergeJitter(@Nullable JitterConfig config, @Nullable JitterConfig defaultConfig) {
        if (config == null && defaultConfig == null) {
            return null;
        }

        return new $RetryConfig_JitterConfig_ConfigValueMapper.JitterConfig_Impl(
            config == null || config.type() == null ? (defaultConfig == null || defaultConfig.type() == null ? JitterType.NONE : defaultConfig.type()) : config.type(),
            config == null || config.ratio() == null ? (defaultConfig == null || defaultConfig.ratio() == null ? 1.0 : defaultConfig.ratio()) : config.ratio()
        );
    }

    @Nullable
    private static RetryBudgetConfig mergeRetryBudget(@Nullable RetryBudgetConfig config, @Nullable RetryBudgetConfig defaultConfig) {
        if (config == null && defaultConfig == null) {
            return null;
        }

        return new $RetryConfig_RetryBudgetConfig_ConfigValueMapper.RetryBudgetConfig_Impl(
            config == null || config.enabled() == null ? (defaultConfig != null && Boolean.TRUE.equals(defaultConfig.enabled())) : Boolean.TRUE.equals(config.enabled()),
            config == null || config.name() == null ? (defaultConfig == null ? null : defaultConfig.name()) : config.name(),
            config == null || config.ratio() == null ? (defaultConfig == null || defaultConfig.ratio() == null ? 0.1 : defaultConfig.ratio()) : config.ratio(),
            config == null || config.maxTokens() == null ? (defaultConfig == null || defaultConfig.maxTokens() == null ? 100 : defaultConfig.maxTokens()) : config.maxTokens(),
            config == null || config.initialTokens() == null ? (defaultConfig == null || defaultConfig.initialTokens() == null ? 10 : defaultConfig.initialTokens()) : config.initialTokens(),
            config == null || config.minTokensPerSecond() == null ? (defaultConfig == null || defaultConfig.minTokensPerSecond() == null ? 0.0 : defaultConfig.minTokensPerSecond()) : config.minTokensPerSecond()
        );
    }
}
