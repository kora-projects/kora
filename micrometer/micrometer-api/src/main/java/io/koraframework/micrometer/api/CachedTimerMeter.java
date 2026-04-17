package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CachedTimerMeter implements TimerMeter {

    private final Map<Tags, Timer> meterCache = new ConcurrentHashMap<>();

    private final Meter.MeterProvider<Timer> provider;

    public CachedTimerMeter(Meter.MeterProvider<Timer> provider) {
        this.provider = provider;
    }

    @Override
    public void recordElapsedFromNanos(long startedInNanos, Supplier<Tags> metricCacheKeyTags) {
        var tookNanos = System.nanoTime() - startedInNanos;
        var tags = metricCacheKeyTags.get();
        var keyTags = (tags == null || !tags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(tags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.record(tookNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordElapsedFromNanos(long startedInNanos, Tags metricCacheKeyTags) {
        var tookNanos = System.nanoTime() - startedInNanos;
        var keyTags = (metricCacheKeyTags == null || !metricCacheKeyTags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(metricCacheKeyTags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.record(tookNanos, TimeUnit.NANOSECONDS);
    }
}
