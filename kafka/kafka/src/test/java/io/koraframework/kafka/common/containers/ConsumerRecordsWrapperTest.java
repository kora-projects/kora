package io.koraframework.kafka.common.containers;

import io.koraframework.kafka.common.consumer.containers.ConsumerRecordsWrapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerRecordsWrapperTest {

    private static ConsumerRecord<byte[], byte[]> record(String topic, long offset, String value) {
        return new ConsumerRecord<>(topic, 0, offset, "k".getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void recordsByTopicReturnsDeserializedRecordsOfThatTopic() {
        var orders = new TopicPartition("orders", 0);
        var payments = new TopicPartition("payments", 0);
        var raw = new ConsumerRecords<>(Map.of(
            orders, List.of(record("orders", 0, "o1"), record("orders", 1, "o2")),
            payments, List.of(record("payments", 0, "p1"))
        ), Map.of());
        var wrapped = new ConsumerRecordsWrapper<>(raw, new StringDeserializer(), new StringDeserializer());

        assertThat(wrapped.records("orders")).extracting(ConsumerRecord::value).containsExactly("o1", "o2");
        assertThat(wrapped.records("payments")).extracting(ConsumerRecord::value).containsExactly("p1");
        assertThat(wrapped.records("unknown")).isEmpty();
    }

    @Test
    void nextOffsetsAreTakenFromPolledRecords() {
        var orders = new TopicPartition("orders", 0);
        var nextOffsets = Map.of(orders, new OffsetAndMetadata(42L));
        var raw = new ConsumerRecords<>(Map.of(orders, List.of(record("orders", 41, "o1"))), nextOffsets);
        var wrapped = new ConsumerRecordsWrapper<>(raw, new StringDeserializer(), new StringDeserializer());

        assertThat(wrapped.nextOffsets()).isEqualTo(nextOffsets);
    }
}
