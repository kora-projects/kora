package io.koraframework.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.koraframework.kafka.common.producer.telemetry.*;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherMetricsFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.NoopKafkaPublisherLoggerFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.NoopKafkaPublisherTelemetry;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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

    @Test
    void sendSpansInsideTransactionAreChildrenOfTransactionSpan() {
        var spans = new CopyOnWriteArrayList<SpanData>();
        var tracer = SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {}

            @Override
            public boolean isStartRequired() {
                return false;
            }

            @Override
            public void onEnd(ReadableSpan span) {
                spans.add(span.toSpanData());
            }

            @Override
            public boolean isEndRequired() {
                return true;
            }
        }).build().get("test");
        var config = new $KafkaPublisherTelemetryConfig_ConfigValueMapper.KafkaPublisherTelemetryConfig_Impl(
            new $KafkaPublisherTelemetryConfig_KafkaProducerLoggingConfig_ConfigValueMapper.KafkaProducerLoggingConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerMetricsConfig_ConfigValueMapper.KafkaProducerMetricsConfig_Defaults(),
            new $KafkaPublisherTelemetryConfig_KafkaProducerTracingConfig_ConfigValueMapper.KafkaProducerTracingConfig_Defaults());
        var telemetry = new DefaultKafkaPublisherTelemetry("test", "foo.TestPublisher", config, tracer,
            DefaultKafkaPublisherTelemetryFactory.NOOP_METER_REGISTRY, DefaultKafkaPublisherMetricsFactory.INSTANCE,
            NoopKafkaPublisherLoggerFactory.INSTANCE, new Properties());
        var producer = new MockProducer<>(true, null, new ByteArraySerializer(), new ByteArraySerializer());
        var publisher = new GeneratedPublisher() {
            @Override
            public void init() {}

            @Override
            public void release() {}

            @Override
            public Producer<byte[], byte[]> producer() {
                return producer;
            }

            @Override
            public KafkaPublisherTelemetry telemetry() {
                return telemetry;
            }
        };
        var p = new TransactionalPublisherImpl<GeneratedPublisher>(
            new $KafkaPublisherConfig_TransactionConfig_ConfigValueMapper.TransactionConfig_Impl("test-", 1, Duration.ofSeconds(1)),
            () -> publisher
        );

        // what a generated @KafkaPublisher method does for one record
        Runnable send = () -> {
            var observation = publisher.telemetry().observeSend("topic");
            var record = new ProducerRecord<byte[], byte[]>("topic", "v".getBytes(StandardCharsets.UTF_8));
            observation.observeRecord(record);
            publisher.producer().send(record, observation);
        };
        var outer = tracer.spanBuilder("outer").startSpan();
        ScopedValue.where(OpentelemetryContext.VALUE, Context.current().with(outer)).run(() -> {
            p.inTx((TransactionalPublisher.TransactionalConsumer<GeneratedPublisher, RuntimeException>) pub -> send.run());
            p.withTx((TransactionalPublisher.TransactionConsumer<GeneratedPublisher, RuntimeException>) tx -> send.run());
        });
        outer.end();

        var txSpans = spans.stream().filter(s -> s.getName().equals("producer transaction")).toList();
        var sendSpans = spans.stream().filter(s -> s.getName().equals("send topic")).toList();
        assertThat(txSpans).hasSize(2);
        assertThat(sendSpans).hasSize(2);
        for (int i = 0; i < 2; i++) {
            assertThat(sendSpans.get(i).getParentSpanId()).isEqualTo(txSpans.get(i).getSpanId());
            assertThat(sendSpans.get(i).getTraceId()).isEqualTo(txSpans.get(i).getTraceId());
            assertThat(txSpans.get(i).getParentSpanId()).isEqualTo(outer.getSpanContext().getSpanId());
            assertThat(txSpans.get(i).getTraceId()).isEqualTo(outer.getSpanContext().getTraceId());
        }
    }
}
