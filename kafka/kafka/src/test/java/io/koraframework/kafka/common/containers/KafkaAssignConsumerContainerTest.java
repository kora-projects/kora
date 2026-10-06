package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import io.koraframework.common.Either;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.containers.KafkaAssignConsumerContainer;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.*;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

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

    private KafkaListenerConfig config(Properties driverProps, String topic, Duration backoff, Duration shutdownWait, int threads) {
        return new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps,
            List.of(topic),
            null,
            null,
            Either.right(KafkaListenerConfig.Offset.earliest),
            Duration.ofMillis(100),
            backoff,
            Integer.valueOf(threads),
            Duration.ofMillis(10000),
            shutdownWait,
            false,
            null,
            new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueMapper.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
            )
        );
    }

    private static void await(BooleanSupplier condition, long millis) throws InterruptedException {
        var end = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
    }

    private static List<Long> intervals(List<Long> times) {
        var intervals = new ArrayList<Long>();
        for (int i = 1; i < times.size(); i++) {
            intervals.add(times.get(i) - times.get(i - 1));
        }
        return intervals;
    }

    private Properties driverProps() {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return driverProps;
    }

    private static RecordHandler<String, String> alwaysFailing(List<Long> times) {
        return new RecordHandler<>(true, () -> (consumer, observation, record) -> {
            times.add(System.currentTimeMillis());
            throw new IllegalStateException("boom");
        });
    }

    private static KafkaConsumerTelemetry recordingTelemetry(List<KafkaConsumerPollObservation> observations) {
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        Mockito.when(telemetry.observePoll()).thenAnswer(i -> {
            var observation = Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
            observations.add(observation);
            return observation;
        });
        return telemetry;
    }

    @Test
    void listenerSurvivesErrorThrownByHandler() throws InterruptedException {
        var topic = params.createTopic("error-assign", 1);
        params.send(topic, 0, "k", "v0");
        var seen = new CopyOnWriteArrayList<String>();
        var failOnce = new AtomicInteger();
        var observations = new CopyOnWriteArrayList<KafkaConsumerPollObservation>();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            seen.add(record.value());
            if (failOnce.getAndIncrement() == 0) {
                throw new AssertionError("not implemented yet");
            }
        });
        var container = new KafkaAssignConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofMillis(200), Duration.ofSeconds(5), 1),
            new StringDeserializer(), new StringDeserializer(), recordingTelemetry(observations), handler);
        try {
            container.init();
            await(() -> !seen.isEmpty(), 30000);
            params.send(topic, 0, "k", "v1");
            await(() -> seen.contains("v1"), 20000);
        } finally {
            container.release();
        }
        assertThat(seen).as("records handled after the handler threw an Error once").contains("v0", "v1");
        for (var observation : observations) {
            Mockito.verify(observation, Mockito.atMost(1)).end();
        }
    }

    @Test
    void backoffDoublesWhenOtherThreadIsHealthy() throws InterruptedException {
        var topic = params.createTopic("backoff-threads-assign", 2);
        params.send(topic, 0, "k", "poison");
        var times = new CopyOnWriteArrayList<Long>();
        var container = new KafkaAssignConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofSeconds(2), Duration.ofSeconds(1), 2),
            new StringDeserializer(), new StringDeserializer(), new NoopKafkaConsumerTelemetry(), alwaysFailing(times));
        try {
            container.init();
            await(() -> times.size() >= 4, 60000);
        } finally {
            container.release();
        }
        var intervals = intervals(times);
        // backoff doubles on repeated errors: 2s, 4s, 8s
        assertThat(intervals).hasSizeGreaterThanOrEqualTo(3);
        assertThat(intervals.get(2)).as("retry intervals of the poison record with threads=2: %s", intervals).isGreaterThanOrEqualTo(7000);
    }

    @Test
    void backoffCappedAt60s() throws InterruptedException {
        var topic = params.createTopic("backoff-cap-assign", 1);
        params.send(topic, 0, "k", "poison");
        var times = new CopyOnWriteArrayList<Long>();
        var container = new KafkaAssignConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofSeconds(31), Duration.ofSeconds(1), 1),
            new StringDeserializer(), new StringDeserializer(), new NoopKafkaConsumerTelemetry(), alwaysFailing(times));
        try {
            container.init();
            await(() -> times.size() >= 3, 150000);
        } finally {
            container.release();
        }
        var intervals = intervals(times);
        assertThat(intervals).hasSizeGreaterThanOrEqualTo(2);
        assertThat(intervals.get(1)).as("retry intervals with backoffTimeout=31s: %s", intervals).isLessThanOrEqualTo(61000);
    }

    @Test
    void releaseDuringBackoffDoesNotWaitForBackoff() throws InterruptedException {
        var topic = params.createTopic("backoff-release-assign", 1);
        params.send(topic, 0, "k", "v");
        var times = new CopyOnWriteArrayList<Long>();
        var container = new KafkaAssignConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofSeconds(20), Duration.ofSeconds(30), 1),
            new StringDeserializer(), new StringDeserializer(), new NoopKafkaConsumerTelemetry(), alwaysFailing(times));
        container.init();
        await(() -> !times.isEmpty(), 30000);
        assertThat(times).isNotEmpty();
        Thread.sleep(500); // backing off now, no record in flight
        var started = System.nanoTime();
        container.release();
        var tookMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(tookMs).as("release() took %sms while no record was in flight", tookMs).isLessThan(5000);
    }

    @Test
    void lagIsZeroAfterConsumingTransactionalTopic() throws Exception {
        var topic = params.createTopic("lag-tx", 1);
        var producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "lag-" + UUID.randomUUID());
        try (var producer = new KafkaProducer<>(producerProps, new StringSerializer(), new StringSerializer())) {
            producer.initTransactions();
            producer.beginTransaction();
            for (int i = 0; i < 3; i++) {
                producer.send(new ProducerRecord<>(topic, 0, "k", "v" + i)).get();
            }
            producer.commitTransaction();
        }
        var registry = new SimpleMeterRegistry();
        var telemetryConfig = new KafkaConsumerTelemetryConfig() {
            @Override
            public KafkaConsumerLoggingConfig logging() {
                return new KafkaConsumerLoggingConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                };
            }

            @Override
            public KafkaConsumerMetricsConfig metrics() {
                return new KafkaConsumerMetricsConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }
                };
            }

            @Override
            public KafkaConsumerTracingConfig tracing() {
                return new KafkaConsumerTracingConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                };
            }
        };
        var driverProps = driverProps();
        driverProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        var telemetry = new DefaultKafkaConsumerTelemetryFactory(null, registry, null, null)
            .get("test", "pkg.Listener.process", driverProps, telemetryConfig);
        var seen = new CopyOnWriteArrayList<String>();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> seen.add(record.value()));
        var container = new KafkaAssignConsumerContainer<>("test", "test",
            config(driverProps, topic, Duration.ofSeconds(1), Duration.ofSeconds(5), 1),
            new StringDeserializer(), new StringDeserializer(), telemetry, handler);
        try {
            container.init();
            await(() -> seen.size() >= 3, 30000);
            Thread.sleep(1000);
        } finally {
            container.release();
        }
        assertThat(seen).containsExactly("v0", "v1", "v2");
        var lag = registry.get("messaging.kafka.consumer.lag").tag("messaging.destination.partition.id", "0").gauge().value();
        assertThat(lag).as("consumer lag after every committed record was consumed").isEqualTo(0.0);
    }
}
