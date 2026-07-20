package io.koraframework.resilient.circuitbreaker;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface CircuitBreakerConfig {

    String DEFAULT = "default";
    int STRIPED_APPROX_DEFAULT_STRIPES = 16;
    int STRIPED_APPROX_MAX_STRIPES = 64;
    int TIME_BASED_DEFAULT_SAMPLE_COUNT = 16;
    int TIME_BASED_MAX_SAMPLE_COUNT = 1024;
    int TIME_BASED_DEFAULT_COUNTER_STRIPES = 16;
    int TIME_BASED_MAX_COUNTER_STRIPES = 64;

    default Map<String, NamedConfig> circuitbreaker() {
        return Map.of();
    }

    CircuitBreakerTelemetryConfig telemetry();

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

    /**
     * You can use <a href="https://resilience4j.readme.io/docs/circuitbreaker">Resilient4j documentation</a> as a description of how CircuitBreaker works and how similar properties are configution its parts
     * <p>
     * {@link #failureRateThreshold} Configures the failure rate threshold in percentage. If the failure rate is equal to or greater than the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls. The threshold must be greater than 0 and not greater than 100.<br>
     * {@link #waitDurationInOpenState} Configures an interval function with a fixed wait duration which controls how long the CircuitBreaker should stay open, before it switches to half open.<br>
     * {@link #permittedCallsInHalfOpenState} Configures the number of permitted calls that must succeed when the CircuitBreaker is half open.<br>
     * {@link #countBased} Configures count-based implementations.<br>
     * {@link #timeBased} Configures time-based implementation.<br>
     * {@link #type} Selects implementation trade-off: {@link CircuitBreakerType#FIXED_WINDOW} for the cheapest fixed counter,
     * {@link CircuitBreakerType#STRIPED_APPROX} for the fastest approximate high-concurrency mode, or
     * {@link CircuitBreakerType#RING_BUFFER} for exact global count-based order with higher coordination overhead, or
     * {@link CircuitBreakerType#TIME_BASED} for a fixed-memory time window backed by a LeapArray.<br>
     * {@link #minimumRequiredCalls} Configures the minimum number of calls which are required (per fixed window period) before the CircuitBreaker can calculate the error rate.<br>
     * {@link #failurePredicateName} {@link CircuitBreakerPredicate#name()} default is {@link KoraCircuitBreakerPredicate}<br>
     */
    @ConfigMapper
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        CircuitBreakerType type();

        @Nullable
        CountBasedConfig countBased();

        @Nullable
        TimeBasedConfig timeBased();

        @Nullable
        Integer failureRateThreshold();

        @Nullable
        Duration waitDurationInOpenState();

        @Nullable
        Integer permittedCallsInHalfOpenState();

        @Nullable
        Long minimumRequiredCalls();

        default String failurePredicateName() {
            return KoraCircuitBreakerPredicate.class.getCanonicalName();
        }
    }

    @ConfigMapper
    interface CountBasedConfig {

        @Nullable
        Long windowSize();

        @Nullable
        StripedApproxConfig stripedApprox();
    }

    @ConfigMapper
    interface StripedApproxConfig {

        @Nullable
        Integer stripes();
    }

    @ConfigMapper
    interface TimeBasedConfig {

        @Nullable
        Duration windowDuration();

        @Nullable
        Integer sampleCount();

        @Nullable
        Integer counterStripes();

        @Nullable
        TimeBasedCounterType counterType();
    }

    default NamedConfig getNamedConfig(String name) {
        final NamedConfig defaultConfig = circuitbreaker().get(DEFAULT);
        final NamedConfig namedConfig = circuitbreaker().getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("CircuitBreaker no configuration is provided, but either '%s' or '%s' config is required".formatted(name, DEFAULT));

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.failureRateThreshold() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("failureRateThreshold", name, DEFAULT));
        if (mergedConfig.waitDurationInOpenState() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("waitDurationInOpenState", name, DEFAULT));
        if (mergedConfig.permittedCallsInHalfOpenState() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("permittedCallsInHalfOpenState", name, DEFAULT));
        if (mergedConfig.type() == CircuitBreakerType.TIME_BASED) {
            if (mergedConfig.timeBased() == null || mergedConfig.timeBased().windowDuration() == null) {
                throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                    .formatted("timeBased.windowDuration", name, DEFAULT));
            }
        } else if (mergedConfig.countBased() == null || mergedConfig.countBased().windowSize() == null) {
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("countBased.windowSize", name, DEFAULT));
        }
        if (mergedConfig.minimumRequiredCalls() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("minimumRequiredCalls", name, DEFAULT));

        if (mergedConfig.type() == null)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                .formatted(name, "type"));
        if (mergedConfig.countBased() == null)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                .formatted(name, "countBased"));
        if (mergedConfig.countBased().stripedApprox() == null)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                .formatted(name, "countBased.stripedApprox"));
        if (mergedConfig.timeBased() == null)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is not configured"
                .formatted(name, "timeBased"));
        if (mergedConfig.minimumRequiredCalls() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "minimumRequiredCalls", mergedConfig.minimumRequiredCalls()));
        if (mergedConfig.type() != CircuitBreakerType.TIME_BASED && mergedConfig.countBased().windowSize() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "countBased.windowSize", mergedConfig.countBased().windowSize()));
        if (mergedConfig.permittedCallsInHalfOpenState() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "permittedCallsInHalfOpenState", mergedConfig.permittedCallsInHalfOpenState()));
        if (mergedConfig.type() != CircuitBreakerType.TIME_BASED && mergedConfig.countBased().windowSize() > 0x7FFF_FFFFL)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "countBased.windowSize", 0x7FFF_FFFFL, mergedConfig.countBased().windowSize()));
        if (mergedConfig.countBased().stripedApprox().stripes() == null || mergedConfig.countBased().stripedApprox().stripes() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "countBased.stripedApprox.stripes", mergedConfig.countBased().stripedApprox().stripes()));
        if (mergedConfig.countBased().stripedApprox().stripes() > STRIPED_APPROX_MAX_STRIPES)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "countBased.stripedApprox.stripes", STRIPED_APPROX_MAX_STRIPES, mergedConfig.countBased().stripedApprox().stripes()));
        if (mergedConfig.type() == CircuitBreakerType.STRIPED_APPROX && mergedConfig.countBased().windowSize() > (long) mergedConfig.countBased().stripedApprox().stripes() * 0xFFFFL)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s for '%s' type, but was: %s"
                .formatted(name, "countBased.windowSize", (long) mergedConfig.countBased().stripedApprox().stripes() * 0xFFFFL, CircuitBreakerType.STRIPED_APPROX, mergedConfig.countBased().windowSize()));
        if (mergedConfig.type() == CircuitBreakerType.TIME_BASED && mergedConfig.timeBased().windowDuration().isZero()
            || mergedConfig.type() == CircuitBreakerType.TIME_BASED && mergedConfig.timeBased().windowDuration().isNegative())
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "timeBased.windowDuration", mergedConfig.timeBased().windowDuration()));
        if (mergedConfig.timeBased().sampleCount() == null || mergedConfig.timeBased().sampleCount() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "timeBased.sampleCount", mergedConfig.timeBased().sampleCount()));
        if (mergedConfig.timeBased().sampleCount() > TIME_BASED_MAX_SAMPLE_COUNT)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "timeBased.sampleCount", TIME_BASED_MAX_SAMPLE_COUNT, mergedConfig.timeBased().sampleCount()));
        if (mergedConfig.timeBased().counterStripes() == null || mergedConfig.timeBased().counterStripes() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "timeBased.counterStripes", mergedConfig.timeBased().counterStripes()));
        if (mergedConfig.timeBased().counterStripes() > TIME_BASED_MAX_COUNTER_STRIPES)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "timeBased.counterStripes", TIME_BASED_MAX_COUNTER_STRIPES, mergedConfig.timeBased().counterStripes()));
        if (mergedConfig.type() == CircuitBreakerType.TIME_BASED && toNanosSaturated(mergedConfig.timeBased().windowDuration()) / mergedConfig.timeBased().sampleCount() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' is too small for '%s' value: %s"
                .formatted(name, "timeBased.windowDuration", "timeBased.sampleCount", mergedConfig.timeBased().sampleCount()));
        if (mergedConfig.permittedCallsInHalfOpenState() > 0xFFFF)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be greater than %s, but was: %s"
                .formatted(name, "permittedCallsInHalfOpenState", 0xFFFF, mergedConfig.permittedCallsInHalfOpenState()));
        if (mergedConfig.type() != CircuitBreakerType.TIME_BASED && mergedConfig.minimumRequiredCalls() > mergedConfig.countBased().windowSize())
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' has value %s, it can't be greater than property '%s' which value was: %s"
                .formatted(name, "minimumRequiredCalls", mergedConfig.minimumRequiredCalls(), "countBased.windowSize", mergedConfig.countBased().windowSize()));
        if (mergedConfig.failureRateThreshold() > 100 || mergedConfig.failureRateThreshold() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' failureRateThreshold is percentage and must be in range from 1 to 100, but was: %s"
                .formatted(name, mergedConfig.failureRateThreshold()));

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, @Nullable NamedConfig defaultConfig) {
        return new $CircuitBreakerConfig_NamedConfig_ConfigValueMapper.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig == null || defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.type() == null ? (defaultConfig == null || defaultConfig.type() == null ? CircuitBreakerType.FIXED_WINDOW : defaultConfig.type()) : namedConfig.type(),
            mergeCountBased(namedConfig.countBased(), defaultConfig == null ? null : defaultConfig.countBased()),
            mergeTimeBased(namedConfig.timeBased(), defaultConfig == null ? null : defaultConfig.timeBased()),
            namedConfig.failureRateThreshold() == null ? (defaultConfig == null ? null : defaultConfig.failureRateThreshold()) : namedConfig.failureRateThreshold(),
            namedConfig.waitDurationInOpenState() == null ? (defaultConfig == null ? null : defaultConfig.waitDurationInOpenState()) : namedConfig.waitDurationInOpenState(),
            namedConfig.permittedCallsInHalfOpenState() == null ? (defaultConfig == null ? null : defaultConfig.permittedCallsInHalfOpenState()) : namedConfig.permittedCallsInHalfOpenState(),
            namedConfig.minimumRequiredCalls() == null ? (defaultConfig == null ? null : defaultConfig.minimumRequiredCalls()) : namedConfig.minimumRequiredCalls(),
            namedConfig.failurePredicateName() == null ? (defaultConfig == null ? KoraCircuitBreakerPredicate.class.getCanonicalName() : defaultConfig.failurePredicateName()) : namedConfig.failurePredicateName()
        );
    }

    private static CountBasedConfig mergeCountBased(@Nullable CountBasedConfig config, @Nullable CountBasedConfig defaultConfig) {
        return new $CircuitBreakerConfig_CountBasedConfig_ConfigValueMapper.CountBasedConfig_Impl(
            config == null || config.windowSize() == null ? (defaultConfig == null ? null : defaultConfig.windowSize()) : config.windowSize(),
            mergeStripedApprox(config == null ? null : config.stripedApprox(), defaultConfig == null ? null : defaultConfig.stripedApprox())
        );
    }

    private static TimeBasedConfig mergeTimeBased(@Nullable TimeBasedConfig config, @Nullable TimeBasedConfig defaultConfig) {
        return new $CircuitBreakerConfig_TimeBasedConfig_ConfigValueMapper.TimeBasedConfig_Impl(
            config == null || config.windowDuration() == null ? (defaultConfig == null ? null : defaultConfig.windowDuration()) : config.windowDuration(),
            config == null || config.sampleCount() == null
                ? (defaultConfig == null || defaultConfig.sampleCount() == null ? TIME_BASED_DEFAULT_SAMPLE_COUNT : defaultConfig.sampleCount())
                : config.sampleCount(),
            config == null || config.counterStripes() == null
                ? (defaultConfig == null || defaultConfig.counterStripes() == null ? TIME_BASED_DEFAULT_COUNTER_STRIPES : defaultConfig.counterStripes())
                : config.counterStripes(),
            config == null || config.counterType() == null
                ? (defaultConfig == null || defaultConfig.counterType() == null ? TimeBasedCounterType.ATOMIC : defaultConfig.counterType())
                : config.counterType()
        );
    }

    private static StripedApproxConfig mergeStripedApprox(@Nullable StripedApproxConfig config, @Nullable StripedApproxConfig defaultConfig) {
        return new $CircuitBreakerConfig_StripedApproxConfig_ConfigValueMapper.StripedApproxConfig_Impl(
            config == null || config.stripes() == null
                ? (defaultConfig == null || defaultConfig.stripes() == null ? STRIPED_APPROX_DEFAULT_STRIPES : defaultConfig.stripes())
                : config.stripes()
        );
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
