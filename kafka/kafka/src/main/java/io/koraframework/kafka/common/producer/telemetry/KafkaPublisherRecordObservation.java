package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;

public interface KafkaPublisherRecordObservation extends Callback, Observation {

    void observeData(@Nullable Object key, @Nullable Object value);

    void observeRecord(ProducerRecord<byte[], byte[]> record);
}
