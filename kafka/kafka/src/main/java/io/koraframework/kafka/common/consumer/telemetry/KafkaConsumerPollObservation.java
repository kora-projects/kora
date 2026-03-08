package io.koraframework.kafka.common.consumer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import io.koraframework.common.telemetry.Observation;

public interface KafkaConsumerPollObservation extends Observation {
    KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record);

    void observeRecords(ConsumerRecords<?, ?> records);
}
