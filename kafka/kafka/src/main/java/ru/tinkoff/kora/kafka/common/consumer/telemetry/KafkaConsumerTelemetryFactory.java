package ru.tinkoff.kora.kafka.common.consumer.telemetry;

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
        KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V> emptyCtx = record -> emptyRcdContext;
        return new KafkaConsumerTelemetry<>() {
            @Override
            public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
                return emptyCtx;
            }

            @Override
            public void reportLag(TopicPartition partition, long lag) {

            }
        };
    }

    default KafkaConsumerTelemetry<K, V> get(String consumerName, Properties driverProperties, TelemetryConfig config) {
        return get(driverProperties, config);
    }
}
