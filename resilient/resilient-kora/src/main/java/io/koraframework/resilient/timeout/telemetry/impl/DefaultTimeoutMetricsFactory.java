package io.koraframework.resilient.timeout.telemetry.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultTimeoutMetricsFactory {

    public static final DefaultTimeoutMetricsFactory INSTANCE = new DefaultTimeoutMetricsFactory();

    public DefaultTimeoutMetrics create(DefaultTimeoutTelemetry.TelemetryContext context) {
        return new DefaultTimeoutMetrics(context);
    }

    public static class DefaultTimeoutMetrics {

        public record TimeoutKey(String name,
                                 @Nullable Tags extraTags) {

            public TimeoutKey withExtraTags(Tags tags) {
                return new TimeoutKey(name, tags);
            }
        }

        protected final ConcurrentHashMap<TimeoutKey, Counter> exhaustedCache = new ConcurrentHashMap<>();
        protected final DefaultTimeoutTelemetry.TelemetryContext context;

        public DefaultTimeoutMetrics(DefaultTimeoutTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordTimeout(long timeoutInNanos) {
            var key = createMetricTimeoutKey();
            var meter = this.exhaustedCache.computeIfAbsent(key, k -> createMetricTimeout(k).register(this.context.meterRegistry()));
            meter.increment();
        }

        protected TimeoutKey createMetricTimeoutKey() {
            return new TimeoutKey(this.context.name(), null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricTimeout(TimeoutKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(1 + this.context.config().metrics().tags().size() + extraTags);
            staticTags.add(Tag.of("name", metricKey.name));
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Counter.builder("resilient.timeout.exhausted")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(staticTags));
        }
    }
}
