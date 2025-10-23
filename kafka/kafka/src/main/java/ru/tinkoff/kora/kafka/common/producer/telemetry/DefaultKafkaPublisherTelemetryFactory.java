package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

import java.util.Properties;

public class DefaultKafkaPublisherTelemetryFactory implements KafkaPublisherTelemetryFactory {
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public DefaultKafkaPublisherTelemetryFactory(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaPublisherTelemetry get(String producerName, KafkaPublisherTelemetryConfig config, Properties properties) {
        if (!config.tracing().enabled() && !config.metrics().enabled() && !config.logging().enabled()) {
            return NoopKafkaPublisherTelemetry.INSTANCE;
        }

        return new DefaultKafkaPublisherTelemetry(producerName, config, tracer, meterRegistry, properties);
    }
}
