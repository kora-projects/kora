package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CachedCounterMeter implements CounterMeter {

    private final Map<Tags, Counter> meterCache = new ConcurrentHashMap<>();

    private final Meter.MeterProvider<Counter> provider;

    public CachedCounterMeter(Meter.MeterProvider<Counter> provider) {
        this.provider = provider;
    }

    @Override
    public void recordIncrement(long increment, Supplier<Tags> metricCacheKeyTags) {
        var tags = metricCacheKeyTags.get();
        var keyTags = (tags == null || !tags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(tags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.increment(increment);
    }

    @Override
    public void recordIncrement(long increment, Tags metricCacheKeyTags) {
        var keyTags = (metricCacheKeyTags == null || !metricCacheKeyTags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(metricCacheKeyTags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.increment(increment);
    }
}
