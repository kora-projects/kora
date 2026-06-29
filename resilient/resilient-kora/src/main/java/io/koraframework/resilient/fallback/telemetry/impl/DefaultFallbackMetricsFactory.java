package io.koraframework.resilient.fallback.telemetry.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultFallbackMetricsFactory {

    public static final DefaultFallbackMetricsFactory INSTANCE = new DefaultFallbackMetricsFactory();

    public DefaultFallbackMetrics create(DefaultFallbackTelemetry.TelemetryContext context) {
        return new DefaultFallbackMetrics(context);
    }

    public static class DefaultFallbackMetrics {

        public record ExecuteKey(String name,
                                 String type,
                                 @Nullable Tags extraTags) {

            public ExecuteKey withExtraTags(Tags tags) {
                return new ExecuteKey(name, type, tags);
            }
        }

        protected final ConcurrentHashMap<ExecuteKey, Counter> attemptsCache = new ConcurrentHashMap<>();
        protected final DefaultFallbackTelemetry.TelemetryContext context;

        public DefaultFallbackMetrics(DefaultFallbackTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordExecute(Throwable throwable) {
            var key = createMetricExecuteKey(throwable);
            var meter = this.attemptsCache.computeIfAbsent(key, k -> createMetricExecute(k, throwable).register(this.context.meterRegistry()));
            meter.increment();
        }

        protected ExecuteKey createMetricExecuteKey(Throwable throwable) {
            return new ExecuteKey(this.context.name(), "executed", null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricExecute(ExecuteKey metricKey, Throwable throwable) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(2 + this.context.config().metrics().tags().size() + extraTags);
            staticTags.add(Tag.of("type", metricKey.type));
            staticTags.add(Tag.of("name", metricKey.name));
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Counter.builder("resilient.fallback.attempts")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(staticTags));
        }
    }
}
