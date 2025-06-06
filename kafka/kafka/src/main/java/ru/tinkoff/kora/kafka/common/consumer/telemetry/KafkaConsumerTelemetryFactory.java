package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerTelemetryFactory<K, V> {

    /**
     * @see #get(String, Properties, TelemetryConfig)
     */
    @Deprecated
    default KafkaConsumerTelemetry<K, V> get(Properties driverProperties, TelemetryConfig config) {
        KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> emptyRcdContext = ex -> {};
        KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V> emptyCtx = new KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V>() {
            @Override
            public KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> get(ConsumerRecord<K, V> record) {
                return emptyRcdContext;
            }

            @Override
            public void close(@Nullable Throwable ex) {

            }
        };

        return new KafkaConsumerTelemetry<>() {
            @Override
            public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
                return emptyCtx;
            }

            @Override
            public void reportLag(TopicPartition partition, long lag) {

            }

            @Override
            public KafkaConsumerTelemetryContext<K, V> get(Consumer<K, V> consumer) {
                return () -> {};
            }
        };
    }

    default KafkaConsumerTelemetry<K, V> get(String consumerName, Properties driverProperties, TelemetryConfig config) {
        return get(driverProperties, config);
    }
}
