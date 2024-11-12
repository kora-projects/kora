package ru.tinkoff.kora.kafka.common.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;

/**
 * @see org.apache.kafka.clients.consumer.ConsumerRebalanceListener
 */
public interface ConsumerAwareRebalanceListener {

    /**
     * @see org.apache.kafka.clients.consumer.ConsumerRebalanceListener#onPartitionsRevoked(Collection)
     */
    void onPartitionsRevoked(Consumer<?, ?> consumer, Collection<TopicPartition> partitions);

    /**
     * @see org.apache.kafka.clients.consumer.ConsumerRebalanceListener#onPartitionsAssigned(Collection)
     */
    void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions);

    /**
     * @see org.apache.kafka.clients.consumer.ConsumerRebalanceListener#onPartitionsLost(Collection)
     */
    default void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        onPartitionsRevoked(consumer, partitions);
    }
}
