package ru.tinkoff.kora.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.KafkaSubscribeConsumerContainer;
import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;
import ru.tinkoff.kora.test.kafka.KafkaParams;
import ru.tinkoff.kora.test.kafka.KafkaTestContainer;

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

    @Test
    void test(KafkaParams params) throws InterruptedException {
        var driverProps = new Properties();
        driverProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, params.bootstrapServers());
        driverProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        driverProps.put(CommonClientConfigs.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        var testTopic = params.createTopic("test-topic", 3);
        var config = new KafkaConsumerConfig(driverProps, List.of(testTopic), null, null, Either.right("earliest"), null, null, Integer.valueOf(1), null);
        var queue = new ArrayBlockingQueue<>(3);
        var container = new KafkaSubscribeConsumerContainer<>(config, new StringDeserializer(), new IntegerDeserializer(), (records, consumer, commitAllowed) -> {
            for (var record : records) {
                try {
                    var value = record.value();
                    queue.offer(value);
                } catch (Exception e) {
                    queue.offer(e);
                }
            }
            consumer.commitSync();
        });
        try {
            container.init().block();
            params.send("test-topic", 0, "1", 1);
            assertThat(queue.poll(10, TimeUnit.SECONDS)).isEqualTo(1);
            params.send("test-topic", 1, "2", 2);
            assertThat(queue.poll(10, TimeUnit.SECONDS)).isEqualTo(2);
            params.send("test-topic", 2, "err", "err");
            assertThat(queue.poll(10, TimeUnit.SECONDS)).isInstanceOf(RecordValueDeserializationException.class);
        } finally {
            container.release().block();
        }
    }
}
