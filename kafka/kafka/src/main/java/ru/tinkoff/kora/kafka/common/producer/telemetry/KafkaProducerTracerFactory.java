package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

public interface KafkaProducerTracerFactory {
    KafkaProducerTracer get(Producer<?, ?> producer, Properties properties);
}
