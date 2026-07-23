package io.koraframework.resilient.circuitbreaker;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface CircuitBreakerConfig {

    default boolean enabled() {
        return true;
    }

    default CircuitBreakerType type() {
        return CircuitBreakerType.FIXED_WINDOW;
    }

    @Nullable
    CountBasedConfig countBased();

    @Nullable
    TimeBasedConfig timeBased();

    int failureRateThreshold();

    Duration waitDurationInOpenState();

    int permittedCallsInHalfOpenState();

    long minimumRequiredCalls();

    @Nullable
    default TelemetryConfig telemetry() {
        return null;
    }

    enum CircuitBreakerType {
        /**
         * Lowest overhead implementation with a single packed counter state.
         * It is cheap and simple, but its CLOSED window is fixed-counter based and does not keep exact last-N call history.
         */
        FIXED_WINDOW,
        /**
         * Fastest high-concurrency implementation for hot paths.
         * It spreads writes across independent stripes, so CLOSED statistics are approximate and can drift under uneven load.
         */
        STRIPED_APPROX,
        /**
         * Strong count-based implementation with exact global completion ordering.
         * It uses a global sequencer and slot-level CAS ring buffer, so it is more precise than STRIPED_APPROX but has higher coordination overhead.
         */
        RING_BUFFER,
        /**
         * High-throughput time-based implementation with a fixed LeapArray of time buckets.
         * It does not keep last-N calls; CLOSED statistics cover the latest configured time window and are eventually consistent around bucket rollover.
         */
        TIME_BASED
    }

    @ConfigMapper
    interface CountBasedConfig {

        long windowSize();

        @Nullable
        StripedApproxConfig stripedApprox();
    }

    @ConfigMapper
    interface StripedApproxConfig {

        int STRIPED_APPROX_DEFAULT_STRIPES = 16;
        int STRIPED_APPROX_MAX_STRIPES = 64;

        default int stripes() {
            return STRIPED_APPROX_DEFAULT_STRIPES;
        }
    }

    @ConfigMapper
    interface TimeBasedConfig {

        int TIME_BASED_DEFAULT_SAMPLE_COUNT = 16;
        int TIME_BASED_MAX_SAMPLE_COUNT = 1024;
        int TIME_BASED_DEFAULT_COUNTER_STRIPES = 16;
        int TIME_BASED_MAX_COUNTER_STRIPES = 64;

        Duration windowDuration();

        default int sampleCount() {
            return TIME_BASED_DEFAULT_SAMPLE_COUNT;
        }

        default int counterStripes() {
            return TIME_BASED_DEFAULT_COUNTER_STRIPES;
        }

        default TimeBasedCounterType counterType() {
            return TimeBasedCounterType.ATOMIC;
        }
    }

    enum TimeBasedCounterType {
        /**
         * Striped atomic counters. Default mode: predictable reset semantics and fixed memory.
         */
        ATOMIC,
        /**
         * LongAdder counters. Faster under heavy contention, but bucket reset is more approximate around time boundaries.
         */
        LONG_ADDER
    }

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

    static CircuitBreakerConfig validate(String name, CircuitBreakerConfig config) {
        if (config.type() == CircuitBreakerType.TIME_BASED) {
            if (config.timeBased() == null) {
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                    .formatted(name, "timeBased"));
            }
        } else if (config.countBased() == null) {
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                .formatted(name, "countBased"));
        }

        if (config.minimumRequiredCalls() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "minimumRequiredCalls", config.minimumRequiredCalls()));
        if (config.type() != CircuitBreakerType.TIME_BASED && config.countBased().windowSize() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "countBased.windowSize", config.countBased().windowSize()));
        if (config.permittedCallsInHalfOpenState() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "permittedCallsInHalfOpenState", config.permittedCallsInHalfOpenState()));
        if (config.type() != CircuitBreakerType.TIME_BASED && config.countBased().windowSize() > 0x7FFF_FFFFL)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "countBased.windowSize", 0x7FFF_FFFFL, config.countBased().windowSize()));

        if (config.type() == CircuitBreakerType.STRIPED_APPROX) {
            var stripedApprox = config.countBased().stripedApprox();
            var stripes = stripedApprox == null ? StripedApproxConfig.STRIPED_APPROX_DEFAULT_STRIPES : stripedApprox.stripes();
            if (stripes < 1)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                    .formatted(name, "countBased.stripedApprox.stripes", stripes));
            if (stripes > StripedApproxConfig.STRIPED_APPROX_MAX_STRIPES)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                    .formatted(name, "countBased.stripedApprox.stripes", StripedApproxConfig.STRIPED_APPROX_MAX_STRIPES, stripes));
            if (config.countBased().windowSize() > (long) stripes * 0xFFFFL)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s for '%s' type, but was: %s"
                    .formatted(name, "countBased.windowSize", (long) stripes * 0xFFFFL, CircuitBreakerType.STRIPED_APPROX, config.countBased().windowSize()));
        }

        if (config.type() == CircuitBreakerType.TIME_BASED) {
            var timeBased = config.timeBased();
            if (timeBased.windowDuration().isZero() || timeBased.windowDuration().isNegative())
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                    .formatted(name, "timeBased.windowDuration", timeBased.windowDuration()));
            if (timeBased.sampleCount() < 1)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                    .formatted(name, "timeBased.sampleCount", timeBased.sampleCount()));
            if (timeBased.sampleCount() > TimeBasedConfig.TIME_BASED_MAX_SAMPLE_COUNT)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                    .formatted(name, "timeBased.sampleCount", TimeBasedConfig.TIME_BASED_MAX_SAMPLE_COUNT, timeBased.sampleCount()));
            if (timeBased.counterStripes() < 1)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                    .formatted(name, "timeBased.counterStripes", timeBased.counterStripes()));
            if (timeBased.counterStripes() > TimeBasedConfig.TIME_BASED_MAX_COUNTER_STRIPES)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                    .formatted(name, "timeBased.counterStripes", TimeBasedConfig.TIME_BASED_MAX_COUNTER_STRIPES, timeBased.counterStripes()));
            if (toNanosSaturated(timeBased.windowDuration()) / timeBased.sampleCount() < 1)
                throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is too small for '%s' value: %s"
                    .formatted(name, "timeBased.windowDuration", "timeBased.sampleCount", timeBased.sampleCount()));
        }

        if (config.permittedCallsInHalfOpenState() > 0xFFFF)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "permittedCallsInHalfOpenState", 0xFFFF, config.permittedCallsInHalfOpenState()));
        if (config.type() != CircuitBreakerType.TIME_BASED && config.minimumRequiredCalls() > config.countBased().windowSize())
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' has value %s, it can't be greater than property '%s' which value was: %s"
                .formatted(name, "minimumRequiredCalls", config.minimumRequiredCalls(), "countBased.windowSize", config.countBased().windowSize()));
        if (config.failureRateThreshold() > 100 || config.failureRateThreshold() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' failureRateThreshold is percentage and must be in range from 1 to 100, but was: %s"
                .formatted(name, config.failureRateThreshold()));

        return config;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
