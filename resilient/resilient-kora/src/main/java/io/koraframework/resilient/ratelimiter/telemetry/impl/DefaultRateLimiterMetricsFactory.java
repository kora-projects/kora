package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRateLimiterMetricsFactory {

    public static final DefaultRateLimiterMetricsFactory INSTANCE = new DefaultRateLimiterMetricsFactory();

    public DefaultRateLimiterMetrics create(DefaultRateLimiterTelemetry.TelemetryContext context) {
        return new DefaultRateLimiterMetrics(context);
    }

    public static class DefaultRateLimiterMetrics {

        public record AcquireKey(String name,
                                 String status,
                                 @Nullable Tags extraTags) {

            public AcquireKey withExtraTags(Tags tags) {
                return new AcquireKey(name, status, tags);
            }
        }

        protected final ConcurrentHashMap<AcquireKey, Counter> acquireCache = new ConcurrentHashMap<>();
        protected final DefaultRateLimiterTelemetry.TelemetryContext context;

        public DefaultRateLimiterMetrics(DefaultRateLimiterTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordAcquire(boolean acquired) {
            var key = createMetricAcquireKey(acquired);
            var meter = this.acquireCache.computeIfAbsent(key, k -> createMetricAcquire(k).register(this.context.meterRegistry()));
            meter.increment();
        }

        protected AcquireKey createMetricAcquireKey(boolean acquired) {
            return new AcquireKey(this.context.name(), acquired ? "acquired" : "rejected", null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricAcquire(AcquireKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(2 + this.context.config().metrics().tags().size() + extraTags);
            staticTags.add(Tag.of("name", metricKey.name));
            staticTags.add(Tag.of("status", metricKey.status));
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Counter.builder("resilient.ratelimiter.acquire")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(staticTags));
        }
    }
}
