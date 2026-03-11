package io.koraframework.kafka.common.producer;

import org.apache.kafka.clients.producer.Producer;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;

public interface GeneratedPublisher extends Lifecycle {
    Producer<byte[], byte[]> producer();

    KafkaPublisherTelemetry telemetry();
}
