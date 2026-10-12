package io.koraframework.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.koraframework.kafka.common.producer.telemetry.*;
import io.koraframework.kafka.common.producer.telemetry.impl.NoopKafkaPublisherRecordObservation;
import io.koraframework.kafka.common.producer.telemetry.impl.NoopKafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.impl.NoopKafkaPublisherTransactionObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
        public KafkaPublisherTelemetry telemetry() {
            return NoopKafkaPublisherTelemetry.INSTANCE;
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

        var producerConfig = new $KafkaPublisherConfig_ConfigValueMapper.KafkaPublisherConfig_Impl(producerProps, new $KafkaPublisherTelemetryConfig_ConfigValueMapper.KafkaPublisherTelemetryConfig_Impl(
            new $KafkaPublisherTelemetryConfig_KafkaProducerLoggingConfig_ConfigValueMapper.KafkaProducerLoggingConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerMetricsConfig_ConfigValueMapper.KafkaProducerMetricsConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerTracingConfig_ConfigValueMapper.KafkaProducerTracingConfig_Defaults()
        ));
        var transactionalConfig = new $KafkaPublisherConfig_TransactionConfig_ConfigValueMapper.TransactionConfig_Impl(
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

        var producerConfig = new $KafkaPublisherConfig_ConfigValueMapper.KafkaPublisherConfig_Impl(producerProps, new $KafkaPublisherTelemetryConfig_ConfigValueMapper.KafkaPublisherTelemetryConfig_Impl(
            new $KafkaPublisherTelemetryConfig_KafkaProducerLoggingConfig_ConfigValueMapper.KafkaProducerLoggingConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerMetricsConfig_ConfigValueMapper.KafkaProducerMetricsConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerTracingConfig_ConfigValueMapper.KafkaProducerTracingConfig_Defaults()
        ));
        var transactionalConfig = new $KafkaPublisherConfig_TransactionConfig_ConfigValueMapper.TransactionConfig_Impl(
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
                tx.sendOffsetsToTransaction(Map.of(), new ConsumerGroupMetadata("test"));
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

    private static final class MetricsPublisher extends AbstractPublisher {
        private MetricsPublisher(Properties props, KafkaPublisherTelemetryConfig cfg, KafkaPublisherTelemetry telemetry) {
            super("test", "test", props, cfg, telemetry);
        }
    }

    // every transaction of these publishers fails on commit: a 5000 byte record exceeds max.request.size
    private TransactionalPublisherImpl<MetricsPublisher> failingPool(int maxPoolSize, Duration maxWaitTime, boolean driverMetrics) {
        var telemetry = Mockito.mock(KafkaPublisherTelemetry.class);
        Mockito.when(telemetry.meterRegistry()).thenReturn(new SimpleMeterRegistry());
        Mockito.when(telemetry.observeTx()).thenReturn(NoopKafkaPublisherTransactionObservation.INSTANCE);
        Mockito.when(telemetry.observeSend(Mockito.anyString())).thenReturn(NoopKafkaPublisherRecordObservation.INSTANCE);
        var telemetryConfig = Mockito.mock(KafkaPublisherTelemetryConfig.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(telemetryConfig.metrics().driverMetrics()).thenReturn(driverMetrics);
        Mockito.when(telemetryConfig.logging().enabled()).thenReturn(false);
        var txConfig = new $KafkaPublisherConfig_TransactionConfig_ConfigValueMapper.TransactionConfig_Impl("test-", maxPoolSize, maxWaitTime);
        return new TransactionalPublisherImpl<>(txConfig, () -> {
            var props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tx-" + UUID.randomUUID());
            props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 2000);
            return new MetricsPublisher(props, telemetryConfig, telemetry);
        });
    }

    private static long kafkaMetricsThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.isAlive() && t.getName().contains("micrometer-kafka-metrics")).count();
    }

    @Test
    void failedTxCommitReleasesDriverMetrics() throws Exception {
        var topic = params.createTopic("tx-fail", 1);
        var pool = failingPool(5, Duration.ofSeconds(5), true);
        pool.init();
        var before = kafkaMetricsThreads();
        var failures = 0;
        for (int i = 0; i < 3; i++) {
            try (var tx = pool.begin()) {
                tx.producer().send(new ProducerRecord<>(topic, new byte[5000]));
            } catch (Exception e) {
                failures++;
            }
        }
        pool.release();
        Thread.sleep(500);
        assertThat(failures).isEqualTo(3);
        assertThat(kafkaMetricsThreads() - before).as("micrometer-kafka-metrics threads left after pool release").isZero();
    }

    @Test
    void waiterGetsProducerAfterFailedTransactionFreesSlot() throws Exception {
        var topic = params.createTopic("tx-wait", 1);
        var pool = failingPool(1, Duration.ofSeconds(60), false);
        pool.init();
        try {
            var t0 = System.currentTimeMillis();
            var failedAt = new AtomicLong();
            var a = Thread.ofVirtual().start(() -> {
                try (var tx = pool.begin()) {
                    tx.producer().send(new ProducerRecord<>(topic, new byte[5000]));
                    Thread.sleep(1000);
                } catch (Exception e) {
                    failedAt.set(System.currentTimeMillis() - t0);
                }
            });
            Thread.sleep(300);
            var bResult = new AtomicReference<Object>();
            var bAt = new AtomicLong();
            var b = Thread.ofVirtual().start(() -> {
                try (var tx = pool.begin()) {
                    bResult.set("ok");
                } catch (Exception e) {
                    bResult.set(e);
                }
                bAt.set(System.currentTimeMillis() - t0);
            });
            a.join();
            b.join();
            assertThat(failedAt.get()).as("tx A failed").isPositive();
            assertThat(bResult.get()).as("tx B begin() result; A failed at %sms, B finished at %sms", failedAt.get(), bAt.get()).isEqualTo("ok");
            assertThat(bAt.get() - failedAt.get()).as("B waited after A freed the slot").isLessThan(5000);
        } finally {
            pool.release();
        }
    }
}
