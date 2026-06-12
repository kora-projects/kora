package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface KafkaPublisherTransactionObservation extends Observation {

    void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

    void observeCommit();

    void observeRollback(@Nullable Throwable e);
}
