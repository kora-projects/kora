package ru.tinkoff.kora.micrometer.module.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryOperation;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MicrometerCacheMetrics implements CacheMetrics {

    record DurationKey(String cacheName, String origin, String operationName, String status) {}
    record RatioKey(String cacheName, String origin, String type) {}

    private static final String METRIC_CACHE_DURATION = "cache.duration";

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_STATUS = "status";
    private static final String TAG_TYPE = "type";

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private static final String TYPE_HIT = "hit";
    private static final String TYPE_MISS = "miss";

    private final ConcurrentHashMap<DurationKey, Timer> durations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RatioKey, Counter> counters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    public MicrometerCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSuccess(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache) {
        var key = new DurationKey(operation.cacheName(), operation.origin(), operation.name(), STATUS_SUCCESS);
        var timer = durations.computeIfAbsent(key, k -> {
            var builder = Timer.builder(METRIC_CACHE_DURATION)
                .tag(TAG_CACHE_NAME, k.cacheName())
                .tag(TAG_OPERATION, k.operationName())
                .tag(TAG_ORIGIN, k.origin())
                .tag(TAG_STATUS, k.status());

            return builder.register(meterRegistry);
        });

        timer.record(durationInNanos, TimeUnit.NANOSECONDS);

        if ("GET".startsWith(operation.name())) {
            var ratioType = valueFromCache == null || valueFromCache instanceof Collection<?> vc && !vc.isEmpty() ? TYPE_MISS : TYPE_HIT;
            var ratioKey = new RatioKey(operation.cacheName(), operation.origin(), ratioType);
            var counter = counters.computeIfAbsent(ratioKey, k -> {
                var builder = Counter.builder("cache.ratio")
                    .tag(TAG_CACHE_NAME, k.cacheName())
                    .tag(TAG_ORIGIN, k.origin())
                    .tag(TAG_TYPE, ratioType);

                return builder.register(meterRegistry);
            });
            counter.increment();
        }
    }

    @Override
    public void recordFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable throwable) {
        var key = new DurationKey(operation.cacheName(), operation.origin(), operation.name(), STATUS_FAILED);
        var timer = durations.computeIfAbsent(key, k -> {
            var builder = Timer.builder(METRIC_CACHE_DURATION)
                .tag(TAG_CACHE_NAME, k.cacheName())
                .tag(TAG_OPERATION, k.operationName())
                .tag(TAG_ORIGIN, k.origin())
                .tag(TAG_STATUS, k.status());

            return builder.register(meterRegistry);
        });

        timer.record(durationInNanos, TimeUnit.NANOSECONDS);
    }
}
