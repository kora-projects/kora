package io.koraframework.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.kafka.common.consumer.KafkaListenerConfig;
import io.koraframework.kafka.common.consumer.telemetry.NoopKafkaConsumerTelemetry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import io.koraframework.common.util.Either;
import io.koraframework.kafka.common.consumer.$KafkaListenerConfig_ConfigValueExtractor;
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
        var config = new $KafkaListenerConfig_ConfigValueExtractor.KafkaListenerConfig_Impl(
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
            new $KafkaConsumerTelemetryConfig_ConfigValueExtractor.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueExtractor.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueExtractor.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueExtractor.KafkaConsumerTracingConfig_Defaults()
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
}
