package ru.tinkoff.kora.test.kafka;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public record KafkaParams(String bootstrapServers, String topicPrefix, Set<String> createdTopics) {
    private static Logger logger = LoggerFactory.getLogger(KafkaParams.class);

    public String topic(String name) {
        if (name.startsWith(topicPrefix)) {
            return name;
        }
        return topicPrefix + name;
    }

    public void withAdmin(Consumer<Admin> consumer) {
        withAdmin(1, consumer);
    }

    public void withAdmin(int attempts, Consumer<Admin> consumer) {
        try (var admin = KafkaAdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers))) {
            while (attempts > 0) {
                try {
                    consumer.accept(admin);
                    return;
                } catch (Exception e) {
                    if (attempts == 1) {
                        throw e;
                    }
                    logger.error(e.getMessage(), e);
                    attempts--;
                }
            }
        }
    }

    public <T> void withProducer(Serializer<T> serializer, Consumer<Producer<String, T>> consumer) {
        try (var producer = new KafkaProducer<>(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers), new StringSerializer(), serializer)) {
            consumer.accept(producer);
        }
    }

    public String createTopic(String name, int partitions) {
        var realName = topic(name);
        withAdmin(5, admin -> {
            logger.info("Attempting to create topic {}", realName);
            try {
                admin.createTopics(List.of(new NewTopic(realName, partitions, (short) 1)), new CreateTopicsOptions().timeoutMs(2000)).all().get();
                logger.info("Created : {}", admin.describeTopics(List.of(realName)).allTopicNames().get());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException te) {
                    return;
                }
                throw new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        createdTopics.add(realName);
        return realName;
    }

    @SuppressWarnings("unchecked")
    public <T> void send(String topic, int partition, String key, T body) {
        Serializer<?> serializer = body instanceof Integer ? new IntegerSerializer()
            : body instanceof String ? new StringSerializer()
            : null;

        var realName = topic(topic);
        createdTopics.add(realName);
        withProducer(((Serializer<T>) serializer), p -> {
            try {
                p.send(new ProducerRecord<>(realName, partition, key, body)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public KafkaParams withTopicPrefix(String topicPrefix) {
        return new KafkaParams(
            bootstrapServers, topicPrefix, new HashSet<>()
        );
    }

    public <K, V> ProducerRecord<K, V> producerRecord(String topic, K key, V value) {
        return new ProducerRecord<>(topic(topic), key, value);
    }
}
