package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueMapper;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerTelemetry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void failedRecordIsLoggedWithStacktrace() throws InterruptedException {
        var events = runFailingListener(true);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getMessage()).isEqualTo("KafkaListener record handling failed");
            assertThat(e.getThrowableProxy()).isNotNull();
            assertThat(e.getThrowableProxy().getMessage()).isEqualTo("handler failed");
        });
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getMessage()).isEqualTo("KafkaListener records handling failed");
            assertThat(e.getThrowableProxy()).isNotNull();
            assertThat(e.getThrowableProxy().getMessage()).isEqualTo("handler failed");
        });
    }

    @Test
    void failedRecordIsLoggedWithoutStacktraceWhenDisabled() throws InterruptedException {
        var events = runFailingListener(false);
        assertThat(events)
            .filteredOn(e -> e.getMessage().equals("KafkaListener record handling failed"))
            .isNotEmpty()
            .allSatisfy(e -> {
                assertThat(e.getThrowableProxy()).isNull();
                assertThat(e.getKeyValuePairs()).anySatisfy(kv -> assertThat(kv.key).isEqualTo("exceptionMessage"));
            });
        assertThat(events)
            .filteredOn(e -> e.getMessage().equals("KafkaListener records handling failed"))
            .isNotEmpty()
            .allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
    }

    private List<ILoggingEvent> runFailingListener(boolean stacktrace) throws InterruptedException {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        var testTopic = params.createTopic("error-topic-" + stacktrace, 1);
        var logging = new KafkaConsumerTelemetryConfig.KafkaConsumerLoggingConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean stacktrace() {
                return stacktrace;
            }
        };
        var telemetryConfig = new $KafkaConsumerTelemetryConfig_ConfigValueMapper.KafkaConsumerTelemetryConfig_Impl(
            logging,
            new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueMapper.KafkaConsumerMetricsConfig_Defaults(),
            new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueMapper.KafkaConsumerTracingConfig_Defaults()
        );
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
            false,
            null,
            telemetryConfig
        );
        var listenerImpl = "test.FailingListener" + stacktrace;
        var telemetry = new DefaultKafkaConsumerTelemetryFactory(null, null, null, null)
            .get("test", listenerImpl, driverProps, telemetryConfig);
        var calls = new AtomicInteger();
        var handler = new RecordHandler<String, String>(true, () -> (consumer, observation, record) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("handler failed");
        });

        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var listenerLogger = (Logger) LoggerFactory.getLogger(listenerImpl);
        listenerLogger.setLevel(Level.INFO);
        listenerLogger.addAppender(appender);
        var container = new KafkaSubscribeConsumerContainer<>("test", listenerImpl, config, new StringDeserializer(), new StringDeserializer(), handler, telemetry, null);
        try {
            container.init();
            params.send(testTopic, 0, "k", "v");
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline && appender.list.stream().noneMatch(e -> e.getMessage().equals("KafkaListener records handling failed"))) {
                Thread.sleep(50);
            }
        } finally {
            container.release();
            listenerLogger.detachAppender(appender);
        }
        assertThat(calls.get()).isPositive();
        return List.copyOf(appender.list);
    }
}
