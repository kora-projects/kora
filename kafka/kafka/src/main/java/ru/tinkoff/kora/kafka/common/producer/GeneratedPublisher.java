package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetry;

public interface GeneratedPublisher extends Lifecycle {
    Producer<byte[], byte[]> producer();

    KafkaPublisherTelemetry telemetry();
}
