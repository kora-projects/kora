package ru.tinkoff.kora.opentelemetry.module.kafka.consumer;

import io.opentelemetry.api.trace.Tracer;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracerFactory;

import java.util.Properties;

public class OpentelemetryKafkaProducerTracerFactory implements KafkaProducerTracerFactory {
    private final Tracer tracer;

    public OpentelemetryKafkaProducerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public KafkaProducerTracer get(Producer<?, ?> producer, Properties properties) {
        return new OpentelemetryKafkaProducerTracer(tracer);
    }
}
