package io.koraframework.resilient.retry;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface RetryConfig {

    default boolean enabled() {
        return true;
    }

    Duration delay();

    default Duration delayStep() {
        return Duration.ZERO;
    }

    @Nullable
    BackoffConfig backoff();

    @Nullable
    JitterConfig jitter();

    @Nullable
    RetryBudgetConfig retryBudget();

    int attempts();

    @Nullable
    TelemetryConfig telemetry();

    @ConfigMapper
    interface TelemetryConfig {

        LoggingConfig logging();

        MetricsConfig metrics();

        TracingConfig tracing();

        @ConfigMapper
        interface LoggingConfig {

            @Nullable
            Boolean enabled();
        }

        @ConfigMapper
        interface MetricsConfig {

            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }

        @ConfigMapper
        interface TracingConfig {

            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();
        }
    }

    enum JitterType {
        NONE,
        FULL
    }

    enum BackoffType {
        EXPONENTIAL
    }

    @ConfigMapper
    interface JitterConfig {

        default JitterType type() {
            return JitterType.NONE;
        }

        default double ratio() {
            return 1.0;
        }
    }

    @ConfigMapper
    interface BackoffConfig {

        default BackoffType type() {
            return BackoffType.EXPONENTIAL;
        }

        default double multiplier() {
            return 2.0;
        }

        @Nullable
        Duration delayMax();
    }

    @ConfigMapper
    interface RetryBudgetConfig {

        default boolean enabled() {
            return true;
        }

        default double ratio() {
            return 0.1;
        }

        default int tokensMax() {
            return 100;
        }

        default int tokensInitial() {
            return 10;
        }

        default double minTokensPerSecond() {
            return 0.0;
        }
    }
}
