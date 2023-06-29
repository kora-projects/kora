package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

public interface KafkaProducerLoggerFactory {
    KafkaProducerLogger get(Producer<?, ?> producer, Properties properties);
}
