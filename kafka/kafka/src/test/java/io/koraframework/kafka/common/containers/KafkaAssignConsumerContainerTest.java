package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import io.koraframework.common.Either;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.containers.KafkaAssignConsumerContainer;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import org.jspecify.annotations.Nullable;
import org.mockito.AdditionalAnswers;
import io.koraframework.kafka.common.consumer.telemetry.*;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(KafkaTestContainer.class)
class KafkaAssignConsumerContainerTest {
    static {
        if (LoggerFactory.getLogger("org.apache.kafka") instanceof Logger log) {
            log.setLevel(Level.OFF);
        }
    }

    KafkaParams params;

    @Test
    void test() throws InterruptedException {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var testTopic = params.createTopic("test-topic", 3);
        var config = new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps,
            List.of(testTopic),
            null,
            null,
            Either.right(KafkaListenerConfig.Offset.earliest),
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(2),
            Duration.ofSeconds(1),
            Duration.ofMillis(10000),
            true,
            null,
            new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueMapper.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
            )
        );
        var deque = new ConcurrentLinkedDeque<>();
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        var container = new KafkaAssignConsumerContainer<>("test", "test", config, new StringDeserializer(), new IntegerDeserializer(), telemetry, (observation, records, consumer, commitAllowed) -> {
            for (var record : records) {
                try {
                    deque.offer(record);
                } catch (Exception e) {
                    deque.offer(e);
                }
            }
        });
        try {
            container.init();
            params.withProducer(new IntegerSerializer(), producer -> {
                var latch = new CountDownLatch(100);
                for (int i = 0; i < 100; i++) {
                    var record = new ProducerRecord<>(params.topic("test-topic"), i % 3, String.valueOf(i), i);
                    producer.send(record, (metadata, exception) -> latch.countDown());
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(2000);

            assertThat(deque.size()).isEqualTo(100);
            deque.clear();

            params.withAdmin(admin -> {
                try {
                    admin.createPartitions(Map.of(params.topic("test-topic"), NewPartitions.increaseTo(5))).all().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(1000);

            params.withProducer(new IntegerSerializer(), producer -> {
                var latch = new CountDownLatch(100);
                for (int i = 0; i < 100; i++) {
                    var record = new ProducerRecord<>(params.topic("test-topic"), i % 5, String.valueOf(i), i);
                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            exception.printStackTrace();
                        }
                        latch.countDown();
                    });
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(2000);
            assertThat(deque.size()).isEqualTo(100);
        } finally {
            container.release();
        }
    }

    @Test
    void failedFirstRecordIsRedeliveredAfterRestart() throws InterruptedException {
        var testTopic = params.createTopic("retry-topic", 1);
        var config = config(params.bootstrapServers(), testTopic, Either.right(KafkaListenerConfig.Offset.latest), Duration.ofSeconds(30));
        var attempts = new LinkedBlockingQueue<String>();
        var calls = new AtomicInteger();
        var handler = new RecordHandler<String, String>(false, () -> (consumer, observation, record) -> {
            attempts.add(record.value());
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("transient error");
            }
        });
        var container = new KafkaAssignConsumerContainer<>("test", "test", config, new StringDeserializer(), new StringDeserializer(), NoopKafkaConsumerTelemetry.INSTANCE, handler);
        try {
            container.init();
            Thread.sleep(1000);
            params.send("retry-topic", 0, "k", "v1");
            assertThat(attempts.poll(20, TimeUnit.SECONDS)).isEqualTo("v1");
            assertThat(attempts.poll(20, TimeUnit.SECONDS)).as("failed record must be redelivered after restart").isEqualTo("v1");
        } finally {
            container.release();
        }
    }

    @Test
    void durationOffsetStartsOnEmptyPartition() throws InterruptedException {
        var testTopic = params.createTopic("empty-topic", 1);
        var config = config(params.bootstrapServers(), testTopic, Either.left(Duration.ofMinutes(5)), Duration.ofSeconds(15));
        var queue = new LinkedBlockingQueue<String>();
        var container = new KafkaAssignConsumerContainer<String, String>("test", "test", config, new StringDeserializer(), new StringDeserializer(), NoopKafkaConsumerTelemetry.INSTANCE,
            (observation, records, consumer, commitAllowed) -> records.forEach(r -> queue.add(r.value())));
        try {
            container.init();
            params.send("empty-topic", 0, "k", "v1");
            assertThat(queue.poll(20, TimeUnit.SECONDS)).isEqualTo("v1");
        } finally {
            container.release();
        }
    }

    @Test
    void initializationFailTimeoutMessageNamesListener() {
        var testTopic = params.createTopic("init-topic", 1);
        var config = config(params.bootstrapServers(), testTopic, Either.right(KafkaListenerConfig.Offset.latest), Duration.ofMillis(1));
        var container = new KafkaAssignConsumerContainer<String, String>("kafka.ordersConsumer", "test", config, new StringDeserializer(), new StringDeserializer(), NoopKafkaConsumerTelemetry.INSTANCE,
            (observation, records, consumer, commitAllowed) -> {});
        try {
            assertThatThrownBy(container::init).hasMessage("KafkaListener 'kafka.ordersConsumer' failed to start, due to timeout in 0.001s");
        } finally {
            container.release();
        }
    }

    @Test
    void failedBatchEndsPollObservationOnce() throws InterruptedException {
        var testTopic = params.createTopic("fail-topic", 1);
        params.send("fail-topic", 0, "k", "v1");
        var config = config(params.bootstrapServers(), testTopic, Either.right(KafkaListenerConfig.Offset.earliest), null);
        var observations = new CopyOnWriteArrayList<KafkaConsumerPollObservation>();
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        Mockito.when(telemetry.observePoll()).thenAnswer(i -> {
            var observation = Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
            observations.add(observation);
            return observation;
        });
        var calls = new AtomicInteger();
        var handler = new RecordHandler<String, String>(false, () -> (consumer, observation, record) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        var container = new KafkaAssignConsumerContainer<>("test", "test", config, new StringDeserializer(), new StringDeserializer(), telemetry, handler);
        try {
            container.init();
            for (int i = 0; i < 200 && calls.get() < 1; i++) {
                Thread.sleep(100);
            }
            Thread.sleep(300);
        } finally {
            container.release();
        }

        assertThat(calls.get()).isPositive();
        for (var observation : observations) {
            Mockito.verify(observation, Mockito.atMost(1)).end();
        }
    }

    private static KafkaListenerConfig config(String bootstrapServers, String topic, Either<Duration, KafkaListenerConfig.Offset> offset, @Nullable Duration initializationFailTimeout) {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps,
            List.of(topic),
            null,
            null,
            offset,
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(1),
            Duration.ofSeconds(1),
            Duration.ofMillis(10000),
            true,
            initializationFailTimeout,
            new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueMapper.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
            )
        );
    }
}
