package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultNatsConsumerMetricsFactory {
    public static final DefaultNatsConsumerMetricsFactory INSTANCE = new DefaultNatsConsumerMetricsFactory();

    public DefaultNatsConsumerMetrics create(TelemetryContext context) {
        return new DefaultNatsConsumerMetrics(context);
    }

    public static class DefaultNatsConsumerMetrics {
        protected final TelemetryContext context;
        private final List<Meter> gauges = new ArrayList<>();
        private final ConcurrentHashMap<String, AtomicLong> pending = new ConcurrentHashMap<>();

        public DefaultNatsConsumerMetrics(TelemetryContext context) {
            this.context = context;
        }

        public void duration(String operation, String subject, long started, @Nullable Throwable error) {
            if (!context.metrics()) {
                return;
            }
            var tags = context.tags().and("subject", subject, "operation", operation, "error", error == null ? "" : error.getClass().getName());
            Timer.builder("nats.consumer.duration").tags(tags).serviceLevelObjectives(context.config().metrics().slo())
                .register(context.registry()).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            if (error != null) {
                context.registry().counter("nats.consumer.errors", tags).increment();
            }
        }

        public void count(String operation, String subject, long count) {
            if (context.metrics()) {
                context.registry().counter("nats.consumer.records", context.tags().and("operation", operation, "subject", subject)).increment(count);
            }
        }

        public synchronized void pending(String kind, String subject, long value) {
            if (!context.metrics()) {
                return;
            }
            var key = kind + ":" + subject;
            var holder = pending.computeIfAbsent(key, ignored -> {
                var result = new AtomicLong();
                gauges.add(Gauge.builder("nats.consumer." + kind, result, AtomicLong::get)
                    .tags(context.tags().and("subject", subject)).register(context.registry()));
                return result;
            });
            holder.set(value);
        }

        public synchronized void close() {
            gauges.forEach(context.registry()::remove);
            gauges.clear();
            pending.clear();
        }
    }
}
