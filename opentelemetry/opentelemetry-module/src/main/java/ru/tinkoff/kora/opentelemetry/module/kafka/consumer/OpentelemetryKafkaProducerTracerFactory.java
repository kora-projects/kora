package ru.tinkoff.kora.opentelemetry.module.kafka.consumer;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public class OpentelemetryKafkaProducerTracerFactory implements KafkaProducerTracerFactory {
    private final Tracer tracer;

    public OpentelemetryKafkaProducerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    @Nullable
    public KafkaProducerTracer get(TelemetryConfig.TracingConfig config, Producer<?, ?> producer, Properties properties) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryKafkaProducerTracer(tracer);
        } else {
            return null;
        }
    }
}
