package ru.tinkoff.kora.kafka.common.containers;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ConsumerWrapper<K, V> implements Consumer<K, V> {

    private final Consumer<byte[], byte[]> realConsumer;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;

    public ConsumerWrapper(Consumer<byte[], byte[]> realConsumer, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
        this.realConsumer = realConsumer;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public Set<TopicPartition> assignment() {
        return realConsumer.assignment();
    }

    @Override
    public Set<String> subscription() {
        return realConsumer.subscription();
    }

    @Override
    public void subscribe(Collection<String> topics) {
        realConsumer.subscribe(topics);
    }

    @Override
    public void subscribe(Collection<String> topics, ConsumerRebalanceListener callback) {
        realConsumer.subscribe(topics, callback);
    }

    @Override
    public void assign(Collection<TopicPartition> partitions) {
        realConsumer.assign(partitions);
    }

    @Override
    public void subscribe(Pattern pattern, ConsumerRebalanceListener callback) {
        realConsumer.subscribe(pattern, callback);
    }

    @Override
    public void subscribe(Pattern pattern) {
        realConsumer.subscribe(pattern);
    }

    @Override
    public void unsubscribe() {
        realConsumer.unsubscribe();
    }

    @Override
    @Deprecated
    public ConsumerRecords<K, V> poll(long timeout) {
        return new ConsumerRecordsWrapper<>(realConsumer.poll(timeout), keyDeserializer, valueDeserializer);
    }

    @Override
    public ConsumerRecords<K, V> poll(Duration timeout) {
        return new ConsumerRecordsWrapper<>(realConsumer.poll(timeout), keyDeserializer, valueDeserializer);
    }

    @Override
    public void commitSync() {
        realConsumer.commitSync();
    }

    @Override
    public void commitSync(Duration timeout) {
        realConsumer.commitSync(timeout);
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
        realConsumer.commitSync(offsets);
    }

    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
        realConsumer.commitSync(offsets, timeout);
    }

    @Override
    public void commitAsync() {
        realConsumer.commitAsync();
    }

    @Override
    public void commitAsync(OffsetCommitCallback callback) {
        realConsumer.commitAsync(callback);
    }

    @Override
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
        realConsumer.commitAsync(offsets, callback);
    }

    @Override
    public void seek(TopicPartition partition, long offset) {
        realConsumer.seek(partition, offset);
    }

    @Override
    public void seek(TopicPartition partition, OffsetAndMetadata offsetAndMetadata) {
        realConsumer.seek(partition, offsetAndMetadata);
    }

    @Override
    public void seekToBeginning(Collection<TopicPartition> partitions) {
        realConsumer.seekToBeginning(partitions);
    }

    @Override
    public void seekToEnd(Collection<TopicPartition> partitions) {
        realConsumer.seekToEnd(partitions);
    }

    @Override
    public long position(TopicPartition partition) {
        return realConsumer.position(partition);
    }

    @Override
    public long position(TopicPartition partition, Duration timeout) {
        return realConsumer.position(partition, timeout);
    }

    @Override
    @Deprecated
    public OffsetAndMetadata committed(TopicPartition partition) {
        return realConsumer.committed(partition);
    }

    @Override
    @Deprecated
    public OffsetAndMetadata committed(TopicPartition partition, Duration timeout) {
        return realConsumer.committed(partition, timeout);
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions) {
        return realConsumer.committed(partitions);
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions, Duration timeout) {
        return realConsumer.committed(partitions, timeout);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return realConsumer.metrics();
    }

    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        return realConsumer.partitionsFor(topic);
    }

    @Override
    public List<PartitionInfo> partitionsFor(String topic, Duration timeout) {
        return realConsumer.partitionsFor(topic, timeout);
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics() {
        return realConsumer.listTopics();
    }

    @Override
    public Map<String, List<PartitionInfo>> listTopics(Duration timeout) {
        return realConsumer.listTopics(timeout);
    }

    @Override
    public Set<TopicPartition> paused() {
        return realConsumer.paused();
    }

    @Override
    public void pause(Collection<TopicPartition> partitions) {
        realConsumer.pause(partitions);
    }

    @Override
    public void resume(Collection<TopicPartition> partitions) {
        realConsumer.resume(partitions);
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch) {
        return realConsumer.offsetsForTimes(timestampsToSearch);
    }

    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch, Duration timeout) {
        return realConsumer.offsetsForTimes(timestampsToSearch, timeout);
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
        return realConsumer.beginningOffsets(partitions);
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions, Duration timeout) {
        return realConsumer.beginningOffsets(partitions, timeout);
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
        return realConsumer.endOffsets(partitions);
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions, Duration timeout) {
        return realConsumer.endOffsets(partitions, timeout);
    }

    @Override
    public OptionalLong currentLag(TopicPartition topicPartition) {
        return realConsumer.currentLag(topicPartition);
    }

    @Override
    public ConsumerGroupMetadata groupMetadata() {
        return realConsumer.groupMetadata();
    }

    @Override
    public void enforceRebalance() {
        realConsumer.enforceRebalance();
    }

    @Override
    public void enforceRebalance(String reason) {
        realConsumer.enforceRebalance(reason);
    }

    @Override
    public void close() {
        realConsumer.close();
    }

    @Override
    public void close(Duration timeout) {
        realConsumer.close(timeout);
    }

    @Override
    public void wakeup() {
        realConsumer.wakeup();
    }

    public Consumer<byte[], byte[]> unwrap() {
        return realConsumer;
    }
}
