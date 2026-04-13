package io.koraframework.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;

import java.util.Properties;

public class DefaultKafkaPublisherTelemetryFactory implements KafkaPublisherTelemetryFactory {

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public DefaultKafkaPublisherTelemetryFactory(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaPublisherTelemetry get(String publisherName, String publisherImpl, KafkaPublisherTelemetryConfig config, Properties properties) {
        if (!config.tracing().enabled() && !config.metrics().enabled() && !config.logging().enabled()) {
            return NoopKafkaPublisherTelemetry.INSTANCE;
        }

        var tracer = config.tracing().enabled() ? this.tracer : TracerProvider.noop().get("kafka-producer");
        var meterRegistry = config.metrics().enabled() ? this.meterRegistry : new CompositeMeterRegistry();
        return new DefaultKafkaPublisherTelemetry(publisherName, publisherImpl, config, tracer, meterRegistry, properties);
    }
}
