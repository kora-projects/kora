package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.common.telemetry.Observation;

import java.util.Map;

public interface KafkaPublisherTelemetry {
    MeterRegistry meterRegistry();

    KafkaPublisherTransactionObservation observeTx();

    KafkaPublisherRecordObservation observeSend(String topic);

    interface KafkaPublisherTransactionObservation extends Observation {
        void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

        void observeCommit();

        void observeRollback(@Nullable Throwable e);
    }

    interface KafkaPublisherRecordObservation extends Callback, Observation {
        void observeData(@Nullable Object key, @Nullable Object value);

        void observeRecord(ProducerRecord<byte[], byte[]> record);
    }

}
