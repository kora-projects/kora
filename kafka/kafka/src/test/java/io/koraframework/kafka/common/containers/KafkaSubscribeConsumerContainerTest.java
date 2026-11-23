package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import io.koraframework.common.Either;
import io.koraframework.kafka.common.consumer.containers.KafkaSubscribeConsumerContainer;
import io.koraframework.kafka.common.consumer.telemetry.*;
import io.koraframework.kafka.common.exceptions.RecordValueDeserializationException;
import io.koraframework.test.kafka.KafkaParams;
import io.koraframework.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void initializationFailTimeoutMessageNamesListener() {
        var testTopic = params.createTopic("init-topic", 1);
        var config = config(params.bootstrapServers(), testTopic, Duration.ofMillis(1));
        var container = new KafkaSubscribeConsumerContainer<String, String>("kafka.ordersConsumer", "test", config, new StringDeserializer(), new StringDeserializer(),
            (observation, records, consumer, commitAllowed) -> {}, new NoopKafkaConsumerTelemetry(), null);
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
        var config = config(params.bootstrapServers(), testTopic, null);
        var observations = new CopyOnWriteArrayList<KafkaConsumerPollObservation>();
        var telemetry = Mockito.mock(KafkaConsumerTelemetry.class);
        Mockito.when(telemetry.observePoll()).thenAnswer(i -> {
            var observation = Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
            observations.add(observation);
            return observation;
        });
        var calls = new AtomicInteger();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        var container = new KafkaSubscribeConsumerContainer<>("test", "test", config, new StringDeserializer(), new StringDeserializer(), handler, telemetry, null);
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

    private static KafkaListenerConfig config(String bootstrapServers, String topic, @Nullable Duration initializationFailTimeout) {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        return new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            driverProps,
            List.of(topic),
            null,
            null,
            Either.right(KafkaListenerConfig.Offset.earliest),
            Duration.ofMillis(100),
            Duration.ofMillis(100),
            Integer.valueOf(1),
            Duration.ofMillis(10000),
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
