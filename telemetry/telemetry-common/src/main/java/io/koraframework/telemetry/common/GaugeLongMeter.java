package io.koraframework.telemetry.common;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public class GaugeLongMeter {

    private final Map<Tags, AtomicLong> meterCache = new ConcurrentHashMap<>();

    private final TelemetryConfig.MetricsConfig metricsConfig;
    private final String gaugeName;
    private final Function<Gauge.Builder<AtomicLong>, Gauge> gaugeFunction;

    public GaugeLongMeter(TelemetryConfig.MetricsConfig metricsConfig,
                          String gaugeName,
                          Function<Gauge.Builder<AtomicLong>, Gauge> gaugeFunction) {

        this.metricsConfig = metricsConfig;
        this.gaugeName = gaugeName;
        this.gaugeFunction = gaugeFunction;
    }

    public void recordValue(long value, Supplier<Iterable<Tag>> metricCacheKeyTags) {
        if (!metricsConfig.enabled()) {
            return;
        }

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

    public void recordValue(long value, Iterable<Tag> metricCacheKeyTags) {
        if (!metricsConfig.enabled()) {
            return;
        }

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
