package io.koraframework.resilient.bulkhead;

import io.koraframework.config.common.annotation.ConfigMapper;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@ConfigMapper
public interface BulkheadConfig {

    default boolean enabled() {
        return true;
    }

    /**
     * Maximum number of concurrent calls; must be positive.
     */
    int maxConcurrentCalls();

    @Nullable TelemetryConfig telemetry();

    /**
     * Limit strategy, independent of queue policy.
     */
    default Type type() {
        return Type.FIXED;
    }

    /**
     * Zero rejects immediately; otherwise admission uses a bounded FIFO queue.
     */
    default int maxQueuedCalls() {
        return 0;
    }

    /**
     * Positive timeout required when maxQueuedCalls is nonzero.
     */
    default Duration maxWaitDuration() {
        return Duration.ZERO;
    }

    @Nullable AdaptiveConfig adaptive();

    enum Type {
        FIXED, AIMD, THROUGHPUT
    }

    static BulkheadConfig validate(String name, BulkheadConfig config) {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(config, "config");
        if (name.isBlank() || config.maxConcurrentCalls() <= 0 || config.maxQueuedCalls() < 0 || config.maxWaitDuration().isNegative()
                || (config.maxQueuedCalls() > 0 && config.maxWaitDuration().isZero())) {
            throw new IllegalArgumentException("Invalid Bulkhead name, concurrency limit or queue configuration");
        }
        config.maxWaitDuration().toNanos();
        if (config.type() != Type.FIXED) {
            var adaptive = config.adaptive() == null ? new AdaptiveConfig() {} : config.adaptive();
            if (adaptive.minLimit() <= 0 || adaptive.initialLimit() < adaptive.minLimit()
                    || adaptive.initialLimit() > config.maxConcurrentCalls() || adaptive.increaseStep() <= 0
                    || !Double.isFinite(adaptive.decreaseRatio()) || adaptive.decreaseRatio() <= 0 || adaptive.decreaseRatio() >= 1
                    || adaptive.targetDuration().isNegative() || adaptive.targetDuration().isZero()
                    || adaptive.samplingInterval().isNegative() || adaptive.samplingInterval().isZero() || adaptive.minimumSamples() <= 0
                    || !Double.isFinite(adaptive.throughputTolerance()) || adaptive.throughputTolerance() < 0
                    || adaptive.throughputTolerance() >= 1) {
                throw new IllegalArgumentException("Invalid Bulkhead adaptive configuration");
            }
            adaptive.targetDuration().toNanos();
            adaptive.samplingInterval().toNanos();
        }
        return config;
    }

    @ConfigMapper
    interface AdaptiveConfig {

        default int minLimit() {
            return 1;
        }

        default int initialLimit() {
            return 1;
        }

        default int increaseStep() {
            return 1;
        }

        default double decreaseRatio() {
            return 0.8;
        }

        default Duration targetDuration() {
            return Duration.ofMillis(500);
        }

        default Duration samplingInterval() {
            return Duration.ofSeconds(1);
        }

        default int minimumSamples() {
            return 20;
        }

        default double throughputTolerance() {
            return 0.05;
        }
    }

    @ConfigMapper
    interface TelemetryConfig {

        LoggingConfig logging();

        MetricsConfig metrics();

        TracingConfig tracing();

        @ConfigMapper
        interface LoggingConfig {

            @Nullable Boolean enabled();
        }

        @ConfigMapper
        interface MetricsConfig {

            @Nullable Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable Map<String, String> tags();
        }

        @ConfigMapper
        interface TracingConfig {

            @Nullable Boolean enabled();

            @Nullable Map<String, String> attributes();
        }
    }
}
