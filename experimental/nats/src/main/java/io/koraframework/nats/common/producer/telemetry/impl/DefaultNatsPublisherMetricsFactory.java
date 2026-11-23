package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetry.TelemetryContext;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class DefaultNatsPublisherMetricsFactory {
    public static final DefaultNatsPublisherMetricsFactory INSTANCE = new DefaultNatsPublisherMetricsFactory();

    public DefaultNatsPublisherMetrics create(TelemetryContext context) {
        return new DefaultNatsPublisherMetrics(context);
    }

    public static class DefaultNatsPublisherMetrics {
        protected final TelemetryContext context;

        public DefaultNatsPublisherMetrics(TelemetryContext context) {
            this.context = context;
        }

        public void duration(String operation, String subject, long started, @Nullable Throwable error) {
            if (!context.metrics()) {
                return;
            }
            var tags = context.tags().and("subject", subject, "operation", operation, "error", error == null ? "" : error.getClass().getName());
            Timer.builder("nats.publisher.duration").tags(tags).serviceLevelObjectives(context.config().metrics().slo())
                .register(context.registry()).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            if (error != null) {
                context.registry().counter("nats.publisher.errors", tags).increment();
            }
        }

        public void count(String operation, String subject, long count) {
            if (context.metrics()) {
                context.registry().counter("nats.publisher.records", context.tags().and("operation", operation, "subject", subject)).increment(count);
            }
        }
    }
}
