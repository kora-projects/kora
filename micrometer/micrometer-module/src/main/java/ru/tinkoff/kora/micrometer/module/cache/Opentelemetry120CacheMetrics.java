package ru.tinkoff.kora.micrometer.module.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryOperation;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry120CacheMetrics implements CacheMetrics {

    record DurationKey(String cacheName, String origin, String operationName, String status) {}

    record RatioKey(String cacheName, String origin, String type) {}

    record OpKey(String cacheName, String origin) {}

    private static final String METRIC_CACHE_DURATION = "cache.duration";
    private static final String METRIC_CACHE_RATIO = "cache.ratio";
    private static final String METRIC_CACHE_HIT = "cache.hit";
    private static final String METRIC_CACHE_MISS = "cache.miss";

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_STATUS = "status";
    private static final String TAG_TYPE = "type";

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private static final String TYPE_HIT = "hit";
    private static final String TYPE_MISS = "miss";

    private final ConcurrentHashMap<DurationKey, DistributionSummary> durations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RatioKey, Counter> counters = new ConcurrentHashMap<>();
    @Deprecated(forRemoval = true)
    private final ConcurrentHashMap<OpKey, Counter> missCounters = new ConcurrentHashMap<>();
    @Deprecated(forRemoval = true)
    private final ConcurrentHashMap<OpKey, Counter> hitCounters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry120CacheMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordSuccess(@Nonnull CacheTelemetryOperation op, long durationInNanos, @Nullable Object valueFromCache) {
        final DurationKey key = new DurationKey(op.cacheName(), op.origin(), op.name(), STATUS_SUCCESS);
        durations.computeIfAbsent(key, k -> duration(key, null))
            .record((double) durationInNanos / 1_000_000);


        if ("GET".startsWith(op.name())) {
            final String ratioType;
            var operationKey = new OpKey(op.cacheName(), op.origin());
            if (valueFromCache == null
                || valueFromCache instanceof Collection<?> vc && !vc.isEmpty()
                || valueFromCache instanceof Map<?, ?> mc && !mc.isEmpty()) {
                ratioType = TYPE_MISS;

                var counter = missCounters.computeIfAbsent(operationKey, k -> {
                    var builder = Counter.builder(METRIC_CACHE_MISS)
                        .description("!!! DEPRECATED !!! Please use cache.ratio metric")
                        .tag(TAG_CACHE_NAME, k.cacheName())
                        .tag(TAG_ORIGIN, k.origin());

                    return builder.register(meterRegistry);
                });
                counter.increment();
            } else {
                ratioType = TYPE_HIT;

                var counter = hitCounters.computeIfAbsent(operationKey, k -> {
                    var builder = Counter.builder(METRIC_CACHE_HIT)
                        .description("!!! DEPRECATED !!! Please use cache.ratio metric")
                        .tag(TAG_CACHE_NAME, k.cacheName())
                        .tag(TAG_ORIGIN, k.origin());

                    return builder.register(meterRegistry);
                });
                counter.increment();
            }

            final RatioKey ratioKey = new RatioKey(op.cacheName(), op.origin(), ratioType);
            var counter = counters.computeIfAbsent(ratioKey, k -> {
                var builder = Counter.builder(METRIC_CACHE_RATIO)
                    .tag(TAG_CACHE_NAME, k.cacheName())
                    .tag(TAG_ORIGIN, k.origin())
                    .tag(TAG_TYPE, ratioType);

                return builder.register(meterRegistry);
            });
            counter.increment();
        }
    }

    @Override
    public void recordFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable exception) {
        final DurationKey key = new DurationKey(operation.cacheName(), operation.origin(), operation.name(), STATUS_FAILED);
        durations.computeIfAbsent(key, k -> duration(key, exception))
            .record((double) durationInNanos / 1_000_000);
    }

    private DistributionSummary duration(DurationKey key, @Nullable Throwable exception) {
        var builder = DistributionSummary.builder(METRIC_CACHE_DURATION)
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag(TAG_CACHE_NAME, key.cacheName())
            .tag(TAG_OPERATION, key.operationName())
            .tag(TAG_ORIGIN, key.origin())
            .tag(TAG_STATUS, key.status());

        if (exception != null) {
            builder.tag("error", exception.getClass().getCanonicalName());
        } else {
            builder.tag("error", "");
        }

        return builder.register(meterRegistry);
    }
}
