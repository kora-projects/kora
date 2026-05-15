package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public interface KafkaConsumerPollObservation extends Observation {

    KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record);

    void observeRecords(ConsumerRecords<?, ?> records);
}
