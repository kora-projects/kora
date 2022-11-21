package ru.tinkoff.kora.kafka.common;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.containers.ConsumerWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public interface KafkaConsumerFactory<K, V> {

    Consumer<K, V> buildConsumer();

    static <K, V> KafkaConsumerFactory<K, V> subscribe(KafkaConsumerConfig consumerConfig, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
        return new SubscribeKafkaConsumerFactory<>(consumerConfig, keyDeserializer, valueDeserializer);
    }

    static <K, V> KafkaConsumerFactory<K, V> assign(KafkaConsumerConfig consumerConfig, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
        return new AssignKafkaConsumerFactory<>(consumerConfig, keyDeserializer, valueDeserializer);
    }

    final class SubscribeKafkaConsumerFactory<K, V> implements KafkaConsumerFactory<K, V> {
        private final KafkaConsumerConfig consumerConfig;
        private final Deserializer<K> keyDeserializer;
        private final Deserializer<V> valueDeserializer;

        public SubscribeKafkaConsumerFactory(KafkaConsumerConfig consumerConfig, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
            this.consumerConfig = consumerConfig;
            this.keyDeserializer = keyDeserializer;
            this.valueDeserializer = valueDeserializer;
        }

        @Override
        public Consumer<K, V> buildConsumer() {
            KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerConfig.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());

            try {
                if (consumerConfig.topicsPattern() != null) {
                    consumer.subscribe(consumerConfig.topicsPattern());
                } else if (consumerConfig.topics() != null) {
                    consumer.subscribe(consumerConfig.topics());
                }
            } catch (Exception e) {
                try {
                    consumer.close();
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }

            return new ConsumerWrapper<>(consumer, keyDeserializer, valueDeserializer);
        }
    }

    final class AssignKafkaConsumerFactory<K, V> implements KafkaConsumerFactory<K, V> {
        private final KafkaConsumerConfig consumerConfig;
        private final Deserializer<K> keyDeserializer;
        private final Deserializer<V> valueDeserializer;

        public AssignKafkaConsumerFactory(KafkaConsumerConfig consumerConfig, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
            this.consumerConfig = consumerConfig;
            this.keyDeserializer = keyDeserializer;
            this.valueDeserializer = valueDeserializer;
        }

        @Override
        public Consumer<K, V> buildConsumer() {
            KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerConfig.driverProperties(), new ByteArrayDeserializer(), new ByteArrayDeserializer());

            try {
                var partitions = new ArrayList<TopicPartition>();
                if (consumerConfig.partitions() != null) {
                    for (String partition : consumerConfig.partitions()) {
                        var split = partition.split(":");
                        partitions.add(new TopicPartition(split[0], Integer.parseInt(split[1])));
                    }
                } else {
                    for (String topic : consumerConfig.topics()) {
                        var partitionsInfo = consumer.partitionsFor(topic);
                        for (PartitionInfo info : partitionsInfo) {
                            partitions.add(new TopicPartition(info.topic(), info.partition()));
                        }
                    }
                }

                consumer.assign(partitions);

                if (consumerConfig.offset().left() != null) {
                    var from = System.currentTimeMillis() - consumerConfig.offset().left().toMillis();
                    var seekTimestamps = new HashMap<TopicPartition, Long>();
                    for (var tp : partitions) {
                        seekTimestamps.put(tp, from);
                    }

                    var offsets = consumer.offsetsForTimes(seekTimestamps);
                    for (var tp : partitions) {
                        var offset = offsets.get(tp);
                        if (offset == null) {
                            consumer.seekToEnd(List.of(tp));
                        } else {
                            consumer.seek(tp, offset.offset());
                        }
                    }
                } else {
                    if ("earliest".equals(consumerConfig.offset().right().toLowerCase().trim())) {
                        consumer.seekToBeginning(partitions);
                    } else {
                        consumer.seekToEnd(partitions);
                    }
                }
            } catch (Exception e) {
                try {
                    consumer.close();
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }

            return new ConsumerWrapper<>(consumer, keyDeserializer, valueDeserializer);
        }
    }
}
