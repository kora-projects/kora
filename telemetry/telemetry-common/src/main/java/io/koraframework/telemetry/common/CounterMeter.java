package io.koraframework.telemetry.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class CounterMeter {

    private final Map<Tags, Counter> meterCache = new ConcurrentHashMap<>();

    private final TelemetryConfig.MetricsConfig metricsConfig;
    private final Meter.MeterProvider<Counter> provider;

    public CounterMeter(TelemetryConfig.MetricsConfig metricsConfig,
                        Meter.MeterProvider<Counter> provider) {
        this.metricsConfig = metricsConfig;
        this.provider = provider;
    }

    public void recordIncrement(long increment, Supplier<Iterable<Tag>> metricCacheKeyTags) {
        if (!metricsConfig.enabled()) {
            return;
        }

        var tags = metricCacheKeyTags.get();
        var keyTags = (tags == null || !tags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(tags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.increment(increment);
    }

    public void recordIncrement(long increment, Iterable<Tag> metricCacheKeyTags) {
        if (!metricsConfig.enabled()) {
            return;
        }

        var keyTags = (metricCacheKeyTags == null || !metricCacheKeyTags.iterator().hasNext())
            ? Tags.empty()
            : Tags.of(metricCacheKeyTags);

        var meter = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags));
        meter.increment(increment);
    }
}
