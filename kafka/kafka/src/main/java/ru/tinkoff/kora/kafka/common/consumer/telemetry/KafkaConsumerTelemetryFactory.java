package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerTelemetryFactory<K, V> {
    static <K, V> KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> emptyRecordCtx() {
        return ex -> {};
    }

    static <K, V> KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V> emptyCtx() {
        var emptyRecordCtx = KafkaConsumerTelemetryFactory.<K, V>emptyRecordCtx();

        return new KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V>() {
            @Override
            public KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> get(ConsumerRecord<K, V> record) {
                return emptyRecordCtx;
            }

            @Override
            public void close(@Nullable Throwable ex) {

            }
        };
    }

    static <K, V> KafkaConsumerTelemetry<K, V> empty() {
        var emptyCtx = KafkaConsumerTelemetryFactory.<K, V>emptyCtx();

        return new KafkaConsumerTelemetry<K, V>() {
            @Override
            public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
                return emptyCtx;
            }

            @Override
            public void reportLag(TopicPartition partition, long lag) {

            }
        };
    }

    KafkaConsumerTelemetry<K, V> get(Properties driverProperties, TelemetryConfig config);
}
