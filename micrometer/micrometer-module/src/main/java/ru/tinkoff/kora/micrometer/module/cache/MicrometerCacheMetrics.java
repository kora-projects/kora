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

    record Key(String cacheName, String origin, String operationName, String status) {}

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

    private final ConcurrentHashMap<Key, Timer> durations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RatioKey, Counter> counters = new ConcurrentHashMap<>();
    @Deprecated(forRemoval = true)
    private final ConcurrentHashMap<OpKey, Counter> missCounters = new ConcurrentHashMap<>();
    @Deprecated(forRemoval = true)
    private final ConcurrentHashMap<OpKey, Counter> hitCounters = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    public MicrometerCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSuccess(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache) {
        final Key key = new Key(operation.cacheName(), operation.origin(), operation.name(), STATUS_SUCCESS);
        final Timer timer = durations.computeIfAbsent(key, k -> {
            var builder = Timer.builder(METRIC_CACHE_DURATION)
                .tag(TAG_CACHE_NAME, k.cacheName())
                .tag(TAG_OPERATION, k.operationName())
                .tag(TAG_ORIGIN, k.origin())
                .tag(TAG_STATUS, k.status());

            return builder.register(meterRegistry);
        });

        timer.record(durationInNanos, TimeUnit.NANOSECONDS);

        if ("GET".startsWith(operation.name())) {
            final String ratioType;
            var operationKey = new OpKey(operation.cacheName(), operation.origin());
            if (valueFromCache == null || valueFromCache instanceof Collection<?> vc && !vc.isEmpty()) {
                ratioType = TYPE_MISS;

                var counter = missCounters.computeIfAbsent(operationKey, k -> {
                    var builder = Counter.builder(METRIC_CACHE_MISS)
                        .tag(TAG_CACHE_NAME, k.cacheName())
                        .tag(TAG_ORIGIN, k.origin());

                    return builder.register(meterRegistry);
                });
                counter.increment();
            } else {
                ratioType = TYPE_HIT;

                var counter = hitCounters.computeIfAbsent(operationKey, k -> {
                    var builder = Counter.builder(METRIC_CACHE_HIT)
                        .tag(TAG_CACHE_NAME, k.cacheName())
                        .tag(TAG_ORIGIN, k.origin());

                    return builder.register(meterRegistry);
                });
                counter.increment();
            }

            final RatioKey ratioKey = new RatioKey(operation.cacheName(), operation.origin(), ratioType);
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
    public void recordFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable throwable) {
        final Key key = new Key(operation.cacheName(), operation.origin(), operation.name(), STATUS_FAILED);
        final Timer timer = durations.computeIfAbsent(key, k -> {
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
