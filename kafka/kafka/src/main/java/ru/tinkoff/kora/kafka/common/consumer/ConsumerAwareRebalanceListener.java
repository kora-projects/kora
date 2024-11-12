package ru.tinkoff.kora.kafka.common.consumer;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @see org.apache.kafka.clients.consumer.ConsumerRebalanceListener
 */
public interface ConsumerAwareRebalanceListener {

    /**
     * Calls before partitions/topics been subscribed/assigned, invokes only single time before {@link Consumer#subscribe(Collection, ConsumerRebalanceListener)} called
     */
    default void onPartitionsPrepared(Consumer<?, ?> consumer, @Nullable List<String> topics, @Nullable Pattern topicsPattern) {

    }

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
