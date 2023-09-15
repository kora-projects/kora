package ru.tinkoff.kora.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
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
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.kafka.common.config.$KafkaConsumerConfig_ConfigValueExtractor.KafkaConsumerConfig_Impl;
import ru.tinkoff.kora.kafka.common.consumer.containers.KafkaAssignConsumerContainer;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import ru.tinkoff.kora.test.kafka.KafkaParams;
import ru.tinkoff.kora.test.kafka.KafkaTestContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(KafkaTestContainer.class)
class KafkaAssignConsumerContainerTest {
    static {
        if (LoggerFactory.getLogger("org.apache.kafka") instanceof Logger log) {
            log.setLevel(Level.OFF);
        }
    }

    @Test
    void test(KafkaParams params) throws InterruptedException {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var testTopic = params.createTopic("test-topic", 3);
        var config = new KafkaConsumerConfig_Impl(driverProps, List.of(testTopic), null, null, Either.right("earliest"), Duration.ofMillis(100), Duration.ofMillis(100), Integer.valueOf(2), Duration.ofSeconds(1));
        var deque = new ConcurrentLinkedDeque<>();
        @SuppressWarnings("unchecked")
        var telemetry = (KafkaConsumerTelemetry<String, Integer>) Mockito.mock(KafkaConsumerTelemetry.class);
        var container = new KafkaAssignConsumerContainer<>(config, params.topic("test-topic"), new StringDeserializer(), new IntegerDeserializer(), telemetry, (records, consumer, commitAllowed) -> {
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
}
