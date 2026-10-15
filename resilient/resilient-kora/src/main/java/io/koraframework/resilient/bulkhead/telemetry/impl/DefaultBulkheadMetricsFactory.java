package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.Bulkhead;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.jspecify.annotations.Nullable;

public class DefaultBulkheadMetricsFactory {

    public static final DefaultBulkheadMetricsFactory INSTANCE = new DefaultBulkheadMetricsFactory();

    public DefaultBulkheadMetrics create(DefaultBulkheadTelemetry.TelemetryContext context) {
        return new DefaultBulkheadMetrics(context);
    }

    public static class DefaultBulkheadMetrics {

        public record AcquireKey(String name, String status, @Nullable Tags extraTags) {

            public AcquireKey withExtraTags(Tags tags) {
                return new AcquireKey(name, status, tags);
            }
        }

        protected final ConcurrentHashMap<AcquireKey, Counter> acquireCache = new ConcurrentHashMap<>();
        protected final DefaultBulkheadTelemetry.TelemetryContext context;

        public DefaultBulkheadMetrics(DefaultBulkheadTelemetry.TelemetryContext context) {
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

        public void register(Bulkhead bulkhead) {
            if (!this.context.isMetricsEnabled()) {
                return;
            }
            registerGauge("resilient.bulkhead.in_flight", bulkhead, Bulkhead::inFlight);
            registerGauge("resilient.bulkhead.limit", bulkhead, Bulkhead::currentLimit);
            registerGauge("resilient.bulkhead.max_limit", bulkhead, Bulkhead::maxConcurrentCalls);
            registerGauge("resilient.bulkhead.queue_length", bulkhead, Bulkhead::queueLength);
            registerGauge("resilient.bulkhead.utilization", bulkhead, value -> (double) value.inFlight() / value.currentLimit());
            registerGauge("resilient.bulkhead.saturated", bulkhead, value -> value.inFlight() >= value.currentLimit() ? 1 : 0);
        }

        protected void registerGauge(String name, Bulkhead bulkhead, ToDoubleFunction<Bulkhead> value) {
            var tags = stateTags();
            // Graph refresh creates a new limiter for the same spec; rebind its state gauges.
            var existing = this.context.meterRegistry().find(name).tags(tags).gauge();
            if (existing != null) {
                this.context.meterRegistry().remove(existing);
            }
            Gauge.builder(name, bulkhead, value).tags(tags).strongReference(true).register(this.context.meterRegistry());
        }

        public void recordDuration(long durationNanos) {
            Timer.builder("resilient.bulkhead.duration")
                .tags(stateTags())
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .register(this.context.meterRegistry())
                .record(durationNanos, TimeUnit.NANOSECONDS);
        }

        public void recordQueueWait(long durationNanos) {
            Timer.builder("resilient.bulkhead.queue_wait")
                .tags(stateTags())
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .register(this.context.meterRegistry())
                .record(durationNanos, TimeUnit.NANOSECONDS);
        }

        protected Tags stateTags() {
            return Tags.of("resilient.name", this.context.name())
                .and(
                    this.context.config()
                        .metrics()
                        .tags()
                        .entrySet()
                        .stream()
                        .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                        .toList()
                );
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
            staticTags.add(Tag.of("resilient.name", metricKey.name));
            staticTags.add(Tag.of("resilient.status", metricKey.status));
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Counter.builder("resilient.bulkhead.acquire").baseUnit(BaseUnits.OPERATIONS).tags(Tags.of(staticTags));
        }
    }
}
