package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

public class DefaultKafkaProducerLoggerFactory implements KafkaProducerLoggerFactory {
    @Override
    public KafkaProducerLogger get(Producer<?, ?> producer, Properties properties) {
        return new DefaultKafkaProducerLogger();
    }
}
