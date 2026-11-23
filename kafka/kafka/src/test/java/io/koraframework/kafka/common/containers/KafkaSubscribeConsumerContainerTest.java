package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import io.koraframework.common.Either;
import io.koraframework.kafka.common.consumer.containers.KafkaSubscribeConsumerContainer;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.*;
import io.koraframework.kafka.common.exceptions.RecordValueDeserializationException;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(KafkaTestContainer.class)
class KafkaSubscribeConsumerContainerTest {
    static {
        if (LoggerFactory.getLogger("org.apache.kafka") instanceof Logger log) {
            log.setLevel(Level.OFF);
        }
    }

    KafkaParams params;

    @Test
    void test() throws InterruptedException {
        var p = KafkaTestContainer.getParams();

        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        var testTopic = params.createTopic("test-topic", 3);
        var config = new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps,
            List.of(testTopic),
            null,
            null,
            Either.right(KafkaListenerConfig.Offset.earliest),
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(1),
            Duration.ofMillis(10000),
            Duration.ofMillis(10000),
            true,
            null,
            new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueMapper.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
            )
        );
        var queue = new ArrayBlockingQueue<>(3);
        var container = new KafkaSubscribeConsumerContainer<>("test", "test", config, new StringDeserializer(), new IntegerDeserializer(), (observation, records, consumer, commitAllowed) -> {
            for (var record : records) {
                try {
                    var value = record.value();
                    queue.offer(value);
                } catch (Exception e) {
                    queue.offer(e);
                }
            }
            consumer.commitSync();
        }, new NoopKafkaConsumerTelemetry(), null);
        try {
            container.init();
            params.send("test-topic", 0, "1", 1);
            assertThat(queue.poll(20, TimeUnit.SECONDS)).isEqualTo(1);
            params.send("test-topic", 1, "2", 2);
            assertThat(queue.poll(10, TimeUnit.SECONDS)).isEqualTo(2);
            params.send("test-topic", 2, "err", "err");
            assertThat(queue.poll(10, TimeUnit.SECONDS)).isInstanceOf(RecordValueDeserializationException.class);
        } finally {
            container.release();
        }
    }

    @Test
    void emptyPollObservationIsEnded() throws InterruptedException {
        var topic = params.createTopic("empty-poll", 1);
        var observations = new CopyOnWriteArrayList<KafkaConsumerPollObservation>();
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        Mockito.when(telemetry.observePoll()).thenAnswer(i -> {
            var observation = Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
            observations.add(observation);
            return observation;
        });
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {});
        var container = new KafkaSubscribeConsumerContainer<>("test", "test", config(UUID.randomUUID().toString(), topic),
            new StringDeserializer(), new StringDeserializer(), handler, telemetry, null);
        try {
            container.init();
            var deadline = System.currentTimeMillis() + 20000;
            while (observations.size() <= 5 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        } finally {
            container.release();
        }
        assertThat(observations).hasSizeGreaterThan(5);
        // the last observation may belong to the poll interrupted by release()
        for (var observation : observations.subList(0, observations.size() - 1)) {
            Mockito.verify(observation).end();
        }
    }

    @Test
    void processedRecordNotReportedAsErrorOnShutdown() throws InterruptedException {
        var topic = params.createTopic("shutdown-telemetry", 1);
        params.send(topic, 0, "k", "v");
        var recordObservations = new CopyOnWriteArrayList<KafkaConsumerRecordObservation>();
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        Mockito.when(telemetry.observePoll()).thenAnswer(i -> {
            var observation = Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
            Mockito.doAnswer(inv -> {
                var recordObservation = Mockito.mock(KafkaConsumerRecordObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE.observeRecord(inv.getArgument(0))));
                recordObservations.add(recordObservation);
                return recordObservation;
            }).when(observation).observeRecord(Mockito.any());
            return observation;
        });
        var started = new CountDownLatch(1);
        var finished = new AtomicInteger();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            started.countDown();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            finished.incrementAndGet();
        });
        var group = UUID.randomUUID().toString();
        var container = new KafkaSubscribeConsumerContainer<>("test", "test", config(group, topic),
            new StringDeserializer(), new StringDeserializer(), handler, telemetry, null);
        container.init();
        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
        container.release();

        assertThat(finished.get()).isEqualTo(1);
        var partition = new TopicPartition(topic, 0);
        try (var c = new KafkaConsumer<>(driverProps(group), new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            assertThat(c.committed(Set.of(partition)).get(partition).offset()).isEqualTo(1);
        }
        assertThat(recordObservations).hasSize(1);
        Mockito.verify(recordObservations.getFirst(), Mockito.never()).observeError(Mockito.any());
    }

    private Properties driverProps(String group) {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, group);
        return driverProps;
    }

    private KafkaListenerConfig config(String group, String topic) {
        return new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps(group),
            List.of(topic),
            null,
            null,
            Either.right(KafkaListenerConfig.Offset.earliest),
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(1),
            Duration.ofMillis(10000),
            Duration.ofMillis(10000),
            false,
            null,
            new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueMapper.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
            )
        );
    }
}
