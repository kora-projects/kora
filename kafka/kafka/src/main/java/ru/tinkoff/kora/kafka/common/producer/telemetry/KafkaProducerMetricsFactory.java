package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.Producer;

import javax.annotation.Nullable;
import java.util.Properties;

public interface KafkaProducerMetricsFactory {
    @Nullable
    KafkaProducerMetrics get(Producer<?, ?> producer, Properties properties);
}
