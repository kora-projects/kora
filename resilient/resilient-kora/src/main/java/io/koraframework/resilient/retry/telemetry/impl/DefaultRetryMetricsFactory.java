package io.koraframework.resilient.retry.telemetry.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRetryMetricsFactory {

    public static final DefaultRetryMetricsFactory INSTANCE = new DefaultRetryMetricsFactory();

    public DefaultRetryMetrics create(DefaultRetryTelemetry.TelemetryContext context) {
        return new DefaultRetryMetrics(context);
    }

    public static class DefaultRetryMetrics {

        public record RetryKey(String name,
                               @Nullable Tags extraTags) {

            public RetryKey withExtraTags(Tags tags) {
                return new RetryKey(name, tags);
            }
        }

        protected final ConcurrentHashMap<RetryKey, Counter> attemptCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<RetryKey, Counter> exhaustedCache = new ConcurrentHashMap<>();
        protected final DefaultRetryTelemetry.TelemetryContext context;

        public DefaultRetryMetrics(DefaultRetryTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordAttempt(long delayInNanos) {
            var key = createMetricAttemptKey(delayInNanos);
            var meter = this.attemptCache.computeIfAbsent(key, k -> createMetricAttempt(k, delayInNanos).register(this.context.meterRegistry()));
            meter.increment();
        }

        public void recordExhaustedAttempts(int totalAttempts) {
            var key = createMetricExhaustedKey(totalAttempts);
            var meter = this.exhaustedCache.computeIfAbsent(key, k -> createMetricExhausted(k, totalAttempts).register(this.context.meterRegistry()));
            meter.increment();
        }

        protected RetryKey createMetricAttemptKey(long delayInNanos) {
            return new RetryKey(this.context.name(), null);
        }

        protected RetryKey createMetricExhaustedKey(int totalAttempts) {
            return new RetryKey(this.context.name(), null);
        }

        protected Counter.Builder createMetricAttempt(RetryKey metricKey, long delayInNanos) {
            return createMetricCounter("resilient.retry.attempts", metricKey);
        }

        protected Counter.Builder createMetricExhausted(RetryKey metricKey, int totalAttempts) {
            return createMetricCounter("resilient.retry.exhausted", metricKey);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricCounter(String meterName, RetryKey metricKey) {
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

            return Counter.builder(meterName)
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(staticTags));
        }
    }
}
