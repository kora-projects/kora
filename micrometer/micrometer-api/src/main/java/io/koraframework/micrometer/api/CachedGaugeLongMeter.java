package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CachedGaugeLongMeter implements GaugeLongMeter {

    private final Map<Tags, AtomicLong> meterCache = new ConcurrentHashMap<>();

    private final String gaugeName;
    private final Function<Gauge.Builder<AtomicLong>, Gauge> gaugeFunction;

    public CachedGaugeLongMeter(String gaugeName,
                                Function<Gauge.Builder<AtomicLong>, Gauge> gaugeFunction) {

        this.gaugeName = gaugeName;
        this.gaugeFunction = gaugeFunction;
    }

    @Override
    public void recordValue(long value, Supplier<Tags> metricCacheKeyTags) {
        var tags = metricCacheKeyTags.get();
        var keyTags = (tags == null || !tags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(tags);

        var recorder = meterCache.computeIfAbsent(keyTags, _ -> {
            var counter = new AtomicLong();
            var builder = Gauge.builder(gaugeName, counter, AtomicLong::get)
                .tags(keyTags);

            var meter = gaugeFunction.apply(builder);
            return counter;
        });

        recorder.set(value);
    }

    @Override
    public void recordValue(long value, Tags metricCacheKeyTags) {
        var keyTags = (metricCacheKeyTags == null || !metricCacheKeyTags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(metricCacheKeyTags);

        var recorder = meterCache.computeIfAbsent(keyTags, _ -> {
            var counter = new AtomicLong();
            var builder = Gauge.builder(gaugeName, counter, AtomicLong::get)
                .tags(keyTags);

            var meter = gaugeFunction.apply(builder);
            return counter;
        });

        recorder.set(value);
    }
}
