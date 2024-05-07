package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetry;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.kafka.KafkaParams;
import ru.tinkoff.kora.test.kafka.KafkaTestContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(KafkaTestContainer.class)
class TransactionalPublisherImplTest {
    KafkaParams params;

    private static class CustomKafkaProducer implements GeneratedPublisher {
        private final Producer<byte[], byte[]> producer;

        private CustomKafkaProducer(Producer<byte[], byte[]> producer) {
            this.producer = producer;
        }

        @Override
        public void init() {

        }

        @Override
        public void release() {
            this.producer.close();
        }

        @Override
        public Producer<byte[], byte[]> producer() {
            return producer;
        }

        @Override
        public KafkaProducerTelemetry telemetry() {
            var mock = Mockito.mock(KafkaProducerTelemetry.class);
            Mockito.when(mock.tx()).thenReturn(Mockito.mock(KafkaProducerTelemetry.KafkaProducerTransactionTelemetryContext.class));
            return mock;
        }
    }

    @Test
    void testCommitted() throws Exception {
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

        var producerConfig = new $KafkaPublisherConfig_ConfigValueExtractor.KafkaPublisherConfig_Impl(producerProps, new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
            new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
            new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
            new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null)
        ));
        var transactionalConfig = new $KafkaPublisherConfig_TransactionConfig_ConfigValueExtractor.TransactionConfig_Impl(
            "test-", 5, Duration.ofSeconds(5)
        );

        producerConfig.driverProperties().put(TRANSACTIONAL_ID_CONFIG, transactionalConfig.idPrefix() + "-" + UUID.randomUUID());

        var testTopic = params.createTopic("test-topic", 3);
        var p = new TransactionalPublisherImpl<>(
            transactionalConfig,
            () -> new CustomKafkaProducer(new KafkaProducer<>(producerConfig.driverProperties(), new ByteArraySerializer(), new ByteArraySerializer()))
        );

        var key = "key".getBytes(StandardCharsets.UTF_8);
        var topicPartitions = List.of(
            new TopicPartition(testTopic, 0),
            new TopicPartition(testTopic, 1),
            new TopicPartition(testTopic, 2)
        );
        try (var committed = new KafkaConsumer<>(readCommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer());
             var uncommitted = new KafkaConsumer<>(readUncommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            committed.assign(topicPartitions);
            uncommitted.assign(topicPartitions);
            committed.poll(Duration.ofMillis(100));
            uncommitted.poll(Duration.ofMillis(100));
            p.init();
            p.inTx(pub -> {
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
            });

            uncommitted.seekToBeginning(topicPartitions);
            committed.seekToBeginning(topicPartitions);
            assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(3);
            assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(3);
        } finally {
            p.release();
        }
    }

    @Test
    void testAbort() throws Exception {
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

        var producerConfig = new $KafkaPublisherConfig_ConfigValueExtractor.KafkaPublisherConfig_Impl(producerProps, new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
            new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
            new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
            new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null)
        ));
        var transactionalConfig = new $KafkaPublisherConfig_TransactionConfig_ConfigValueExtractor.TransactionConfig_Impl(
            "test-", 5, Duration.ofSeconds(5)
        );

        producerConfig.driverProperties().put(TRANSACTIONAL_ID_CONFIG, transactionalConfig.idPrefix() + "-" + UUID.randomUUID());

        var testTopic = params.createTopic("test-topic", 3);
        var p = new TransactionalPublisherImpl<>(
            transactionalConfig,
            () -> new CustomKafkaProducer(new KafkaProducer<>(producerConfig.driverProperties(), new ByteArraySerializer(), new ByteArraySerializer()))
        );

        var key = "key".getBytes(StandardCharsets.UTF_8);
        var topicPartitions = List.of(
            new TopicPartition(testTopic, 0),
            new TopicPartition(testTopic, 1),
            new TopicPartition(testTopic, 2)
        );
        try (var committed = new KafkaConsumer<>(readCommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer());
             var uncommitted = new KafkaConsumer<>(readUncommittedProps, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            committed.assign(topicPartitions);
            uncommitted.assign(topicPartitions);
            committed.poll(Duration.ofMillis(100));
            uncommitted.poll(Duration.ofMillis(100));
            p.init();
            p.withTx((tx) -> {
                var pub = tx.publisher();
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                pub.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.flush();

                committed.seekToBeginning(topicPartitions);
                uncommitted.seekToBeginning(topicPartitions);
                assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(3);
                assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(0);

                tx.abort();
            });

            uncommitted.seekToBeginning(topicPartitions);
            committed.seekToBeginning(topicPartitions);
            assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(3);
            assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(0);

            try (var tx = p.begin()) {
                tx.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
                tx.producer().send(params.producerRecord(testTopic, key, "value1".getBytes(StandardCharsets.UTF_8))).get();
            }
            uncommitted.seekToBeginning(topicPartitions);
            committed.seekToBeginning(topicPartitions);
            assertThat(uncommitted.poll(Duration.ofSeconds(1))).hasSize(6);
            assertThat(committed.poll(Duration.ofSeconds(1))).hasSize(3);
        } finally {
            p.release();
        }
    }
}
