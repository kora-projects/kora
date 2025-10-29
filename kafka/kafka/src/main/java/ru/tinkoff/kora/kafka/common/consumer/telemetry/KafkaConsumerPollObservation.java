package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface KafkaConsumerPollObservation extends Observation {
    KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record);

    void observeRecords(ConsumerRecords<?, ?> records);
}
