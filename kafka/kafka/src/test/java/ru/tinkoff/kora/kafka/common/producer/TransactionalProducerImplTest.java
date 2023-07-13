package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.kafka.common.producer.$PublisherConfig_ConfigValueExtractor.PublisherConfig_Impl;
import ru.tinkoff.kora.kafka.common.producer.$PublisherConfig_TransactionConfig_ConfigValueExtractor.TransactionConfig_Impl;
import ru.tinkoff.kora.kafka.common.producer.telemetry.DefaultKafkaProducerTelemetryFactory;
import ru.tinkoff.kora.test.kafka.KafkaParams;
import ru.tinkoff.kora.test.kafka.KafkaTestContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(KafkaTestContainer.class)
class TransactionalProducerImplTest {
    KafkaParams params;

    @Test
    void test() throws InterruptedException, ExecutionException {
        var readCommittedProps = new Properties();
        readCommittedProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        readCommittedProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        readCommittedProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var readUncommittedProps = new Properties();
        readUncommittedProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        readUncommittedProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        readUncommittedProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_uncommitted");

        var producerProps = new Properties();
        producerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());

        var producerTelemetry = new DefaultKafkaProducerTelemetryFactory(null, null, null);
        var producerConfig = new PublisherConfig_Impl(producerProps, new TransactionConfig_Impl(
            "test-", 5, Duration.ofSeconds(5)
        ));

        var testTopic = params.createTopic("test-topic", 3);
        var p = new TransactionalProducerImpl<>(producerTelemetry, producerConfig, new ByteArraySerializer(), new ByteArraySerializer());
        var key = "key".getBytes(StandardCharsets.UTF_8);
        var topicPartition = new TopicPartition(testTopic, 1);
        try (var committed = new KafkaConsumer<>(readCommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer());
             var uncommitted = new KafkaConsumer<>(readUncommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            committed.assign(Set.of(topicPartition));
            uncommitted.assign(Set.of(topicPartition));
            committed.poll(Duration.ofMillis(100));
            uncommitted.poll(Duration.ofMillis(100));
            p.init().block();
            try (var tx = p.begin()) {
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.flush();

                committed.seekToBeginning(Set.of(topicPartition));
                uncommitted.seekToBeginning(Set.of(topicPartition));
                assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(3);
                assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(0);

                tx.abortTransaction();
            }
            uncommitted.seekToBeginning(Set.of(topicPartition));
            committed.seekToBeginning(Set.of(topicPartition));
            assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(3);
            assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(0);

            try (var tx = p.begin()) {
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
            }
            uncommitted.seekToBeginning(Set.of(topicPartition));
            committed.seekToBeginning(Set.of(topicPartition));
            assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(6);
            assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(3);
        } finally {
            p.release().block();
        }
    }
}
