package io.koraframework.telemetry.common;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopTimer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TimerMeter {

    private final Map<Tags, Timer> meterCache = new ConcurrentHashMap<>();

    private final TelemetryConfig metricsConfig;
    private final Tags staticTags;
    private final Meter.MeterProvider<Timer> provider;

    public TimerMeter(TelemetryConfig metricsConfig,
                      List<Tag> staticTags,
                      Meter.MeterProvider<Timer> provider) {
        this(metricsConfig, Tags.of(staticTags), provider);
    }

    public TimerMeter(TelemetryConfig metricsConfig,
                      Tags staticTags,
                      Meter.MeterProvider<Timer> provider) {
        this.metricsConfig = metricsConfig;
        this.provider = provider;
        this.staticTags = staticTags;
    }

    public void recordNanos(long started, Supplier<Iterable<Tag>> metricCacheKeyTags) {
        if(!metricsConfig.metrics().enabled()) {
            return;
        }

        var tookNanos = System.nanoTime() - started;
        var tags = metricCacheKeyTags.get();
        var keyTags = (tags == null || !tags.iterator().hasNext())
                ? Tags.empty()
                : Tags.of(tags);

        var timer = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags.and(staticTags)));
        timer.record(tookNanos, TimeUnit.NANOSECONDS);
    }

    public void recordNanos(long started, Iterable<Tag> metricCacheKeyTags) {
        if(!metricsConfig.metrics().enabled()) {
            return;
        }

        var tookNanos = System.nanoTime() - started;
        var keyTags = (metricCacheKeyTags == null || !metricCacheKeyTags.iterator().hasNext())
                ? Tags.empty()
                : Tags.of(metricCacheKeyTags);

        var timer = meterCache.computeIfAbsent(keyTags, _ -> provider.withTags(keyTags.and(staticTags)));
        timer.record(tookNanos, TimeUnit.NANOSECONDS);
    }
}
