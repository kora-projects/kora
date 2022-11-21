package ru.tinkoff.kora.micrometer.module.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public final class MicrometerCacheMetrics implements CacheMetrics {

    private static final String METRIC_CACHE_DURATION = "cache.duration";
    private static final String METRIC_CACHE_HIT = "cache.hit";
    private static final String METRIC_CACHE_MISS = "cache.miss";

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_STATUS = "status";

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private final MeterRegistry meterRegistry;

    public MicrometerCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSuccess(@Nonnull CacheTelemetry.Operation operation, long durationInNanos, @Nullable Object valueFromCache) {
        final Timer timer = meterRegistry.timer(METRIC_CACHE_DURATION, Tags.of(
            TAG_CACHE_NAME, operation.cacheName(),
            TAG_OPERATION, operation.type().name(),
            TAG_ORIGIN, operation.origin(),
            TAG_STATUS, STATUS_SUCCESS
        ));
        timer.record(durationInNanos, TimeUnit.NANOSECONDS);

        if (CacheTelemetry.Operation.Type.GET == operation.type()) {
            final String metricName = (valueFromCache == null)
                ? METRIC_CACHE_MISS
                : METRIC_CACHE_HIT;

            final Counter counter = meterRegistry.counter(metricName, Tags.of(
                TAG_CACHE_NAME, operation.cacheName(),
                TAG_ORIGIN, operation.origin()
            ));
            counter.increment();
        }
    }

    @Override
    public void recordFailure(@Nonnull CacheTelemetry.Operation operation, long durationInNanos, @Nullable Throwable throwable) {
        final Timer timer = meterRegistry.timer(METRIC_CACHE_DURATION, Tags.of(
            TAG_CACHE_NAME, operation.cacheName(),
            TAG_OPERATION, operation.type().name(),
            TAG_ORIGIN, operation.origin(),
            TAG_STATUS, STATUS_FAILED
        ));
        timer.record(durationInNanos, TimeUnit.NANOSECONDS);
    }
}
