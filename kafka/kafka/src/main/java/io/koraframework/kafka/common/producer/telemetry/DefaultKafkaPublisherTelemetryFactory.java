package io.koraframework.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public final class DefaultKafkaPublisherTelemetryFactory implements KafkaPublisherTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("kafka-publisher");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultKafkaPublisherMetricsFactory metricsFactory;

    public DefaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                 @Nullable MeterRegistry meterRegistry,
                                                 @Nullable DefaultKafkaPublisherMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public KafkaPublisherTelemetry get(String publisherName, String publisherImpl, KafkaPublisherTelemetryConfig config, Properties properties) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopKafkaPublisherTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        DefaultKafkaPublisherMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultKafkaPublisherMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopKafkaPublisherMetricsFactory.INSTANCE;
        }

        return new DefaultKafkaPublisherTelemetry(publisherName, publisherImpl, config, tracer, meterRegistry, enabledMetricsFactory, properties);
    }
}
