package ru.tinkoff.kora.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.kafka.common.consumer.$KafkaListenerConfig_ConfigValueExtractor;
import ru.tinkoff.kora.kafka.common.consumer.containers.KafkaAssignConsumerContainer;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.kafka.KafkaParams;
import ru.tinkoff.kora.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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
        var config = new $KafkaListenerConfig_ConfigValueExtractor.KafkaListenerConfig_Impl(
            driverProps,
            List.of(testTopic),
            null,
            null,
            Either.right("earliest"),
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(2),
            Duration.ofSeconds(1),
            Duration.ofMillis(10000),
            true,
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null, Map.of())
            )
        );
        var deque = new ConcurrentLinkedDeque<>();
        @SuppressWarnings("unchecked")
        var telemetry = (KafkaConsumerTelemetry<String, Integer>) Mockito.mock(KafkaConsumerTelemetry.class);
        var container = new KafkaAssignConsumerContainer<>("test", config, params.topic("test-topic"), new StringDeserializer(), new IntegerDeserializer(), telemetry, (records, consumer, commitAllowed) -> {
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
    void failedFirstBatchIsRedeliveredWithRecordsSentDuringBackoff() throws Exception {
        var topic = params.createTopic("first-batch-fails", 1);
        var failed = new AtomicBoolean();
        var values = new ConcurrentLinkedQueue<Integer>();
        var ready = new CountDownLatch(1);
        var container = container(topic, Either.right("latest"), mockTelemetry(), (records, consumer, commitAllowed) -> {
            ready.countDown();
            for (var record : records) {
                values.add(record.value());
                if (record.value() == 1 && failed.compareAndSet(false, true)) {
                    params.send(topic, 0, "2", 2);
                    throw new IllegalStateException("handler failure");
                }
            }
        });
        try {
            container.init();
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            params.send(topic, 0, "1", 1);

            awaitAtLeast(values, 3);
            assertThat(values).containsExactly(1, 1, 2);
        } finally {
            container.release();
        }
    }

    @Test
    void failedBatchAfterSuccessfulOneIsRedelivered() throws Exception {
        var topic = params.createTopic("second-batch-fails", 1);
        var failed = new AtomicBoolean();
        var values = new ConcurrentLinkedQueue<Integer>();
        var ready = new CountDownLatch(1);
        var container = container(topic, Either.right("latest"), mockTelemetry(), (records, consumer, commitAllowed) -> {
            ready.countDown();
            for (var record : records) {
                values.add(record.value());
                if (record.value() == 2 && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("handler failure");
                }
            }
        });
        try {
            container.init();
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            params.send(topic, 0, "1", 1);
            awaitAtLeast(values, 1);
            params.send(topic, 0, "2", 2);

            awaitAtLeast(values, 3);
            assertThat(values).containsExactly(1, 2, 2);
        } finally {
            container.release();
        }
    }

    @Test
    void lagReportFailureNeitherRestartsConsumerNorRedeliversRecords() throws Exception {
        var topic = params.createTopic("lag-fails", 2);
        send(IntStream.range(0, 20).mapToObj(i -> new ProducerRecord<>(topic, i % 2, String.valueOf(i), i)).toList());
        var telemetry = mockTelemetry();
        Mockito.doThrow(new TimeoutException("end offsets timeout")).when(telemetry).reportLag(Mockito.any(), Mockito.anyLong());
        var values = new ConcurrentLinkedQueue<Integer>();
        var consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        var container = container(topic, Either.right("earliest"), telemetry, (records, consumer, commitAllowed) -> {
            consumers.add(consumer);
            for (var record : records) {
                values.add(record.value());
            }
        });
        try {
            container.init();

            awaitAtLeast(values, 20);
            assertThat(values).containsExactlyInAnyOrderElementsOf(IntStream.range(0, 20).boxed().toList());
            assertThat(consumers).hasSize(1);
        } finally {
            container.release();
        }
    }

    @Test
    void durationOffsetWithoutNewerRecordsStartsFromEnd() throws Exception {
        var topic = params.createTopic("duration-without-newer", 1);
        var hourAgo = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();
        send(List.of(new ProducerRecord<>(topic, 0, hourAgo, "0", 0)));
        var values = new ConcurrentLinkedQueue<Integer>();
        var ready = new CountDownLatch(1);
        var container = container(topic, Either.left(Duration.ofMinutes(1)), mockTelemetry(), (records, consumer, commitAllowed) -> {
            ready.countDown();
            for (var record : records) {
                values.add(record.value());
            }
        });
        try {
            container.init();
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            params.send(topic, 0, "1", 1);

            awaitAtLeast(values, 1);
            assertThat(values).containsExactly(1);
        } finally {
            container.release();
        }
    }

    @Test
    void earliestReadsRecordsSentBeforeStart() throws Exception {
        var topic = params.createTopic("earliest-before-start", 1);
        params.send(topic, 0, "0", 0);
        var values = new ConcurrentLinkedQueue<Integer>();
        var container = container(topic, Either.right("earliest"), mockTelemetry(), (records, consumer, commitAllowed) -> {
            for (var record : records) {
                values.add(record.value());
            }
        });
        try {
            container.init();

            awaitAtLeast(values, 1);
            assertThat(values).containsExactly(0);
        } finally {
            container.release();
        }
    }

    @Test
    void latestSkipsRecordsSentBeforeStart() throws Exception {
        var topic = params.createTopic("latest-before-start", 1);
        params.send(topic, 0, "0", 0);
        var values = new ConcurrentLinkedQueue<Integer>();
        var ready = new CountDownLatch(1);
        var container = container(topic, Either.right("latest"), mockTelemetry(), (records, consumer, commitAllowed) -> {
            ready.countDown();
            for (var record : records) {
                values.add(record.value());
            }
        });
        try {
            container.init();
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            params.send(topic, 0, "1", 1);

            awaitAtLeast(values, 1);
            assertThat(values).containsExactly(1);
        } finally {
            container.release();
        }
    }

    private KafkaAssignConsumerContainer<String, Integer> container(String topic,
                                                                   Either<Duration, String> offset,
                                                                   KafkaConsumerTelemetry<String, Integer> telemetry,
                                                                   BaseKafkaRecordsHandler<String, Integer> handler) {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        var config = new $KafkaListenerConfig_ConfigValueExtractor.KafkaListenerConfig_Impl(
            driverProps,
            List.of(topic),
            null,
            null,
            offset,
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            1,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            true,
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null, Map.of())
            )
        );
        return new KafkaAssignConsumerContainer<>("test", config, topic, new StringDeserializer(), new IntegerDeserializer(), telemetry, handler);
    }

    @SuppressWarnings("unchecked")
    private static KafkaConsumerTelemetry<String, Integer> mockTelemetry() {
        return (KafkaConsumerTelemetry<String, Integer>) Mockito.mock(KafkaConsumerTelemetry.class);
    }

    private void send(List<ProducerRecord<String, Integer>> records) {
        params.withProducer(new IntegerSerializer(), producer -> {
            try {
                for (var record : records) {
                    producer.send(record).get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void awaitAtLeast(Collection<?> values, int size) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (values.size() < size && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(1000);
    }
}
