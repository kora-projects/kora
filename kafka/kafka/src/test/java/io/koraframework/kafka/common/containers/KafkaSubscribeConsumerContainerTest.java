package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.exceptions.RecordValueDeserializationException;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

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
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        return driverProps;
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
        var topic = params.createTopic("error-sub", 1);
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
        var container = new KafkaSubscribeConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofMillis(200), Duration.ofSeconds(5), 1),
            new StringDeserializer(), new StringDeserializer(), handler, recordingTelemetry(observations), null);
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
        var topic = params.createTopic("backoff-threads-sub", 2);
        params.send(topic, 0, "k", "poison");
        var times = new CopyOnWriteArrayList<Long>();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            times.add(System.currentTimeMillis());
            throw new IllegalStateException("boom");
        });
        var container = new KafkaSubscribeConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofSeconds(2), Duration.ofSeconds(1), 2),
            new StringDeserializer(), new StringDeserializer(), handler, new NoopKafkaConsumerTelemetry(), null);
        try {
            container.init();
            await(() -> times.size() >= 6, 90000);
        } finally {
            container.release();
        }
        var intervals = intervals(times);
        // the failing consumer leaves the group on restart, so the poison partition moves between the two consumers,
        // each of them doubles its own backoff: 2s, 2s, 4s, 4s, 8s
        assertThat(intervals).hasSizeGreaterThanOrEqualTo(5);
        assertThat(intervals.get(4)).as("retry intervals of the poison record with threads=2: %s", intervals).isGreaterThanOrEqualTo(7000);
    }

    @Test
    void releaseDuringBackoffDoesNotWaitForBackoff() throws InterruptedException {
        var topic = params.createTopic("backoff-release-sub", 1);
        params.send(topic, 0, "k", "v");
        var calls = new AtomicInteger();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        var container = new KafkaSubscribeConsumerContainer<>("test", "test",
            config(driverProps(), topic, Duration.ofSeconds(20), Duration.ofSeconds(30), 1),
            new StringDeserializer(), new StringDeserializer(), handler, new NoopKafkaConsumerTelemetry(), null);
        container.init();
        await(() -> calls.get() > 0, 30000);
        assertThat(calls.get()).isPositive();
        Thread.sleep(500); // backing off now, no record in flight
        var started = System.nanoTime();
        container.release();
        var tookMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(tookMs).as("release() took %sms while no record was in flight", tookMs).isLessThan(5000);
    }
}
