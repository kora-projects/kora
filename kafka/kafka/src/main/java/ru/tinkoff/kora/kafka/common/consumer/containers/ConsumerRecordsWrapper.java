package ru.tinkoff.kora.kafka.common.consumer.containers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;

import javax.annotation.Nonnull;
import java.util.*;

final class ConsumerRecordsWrapper<K, V> extends ConsumerRecords<K, V> {

    private final ConsumerRecords<byte[], byte[]> realRecords;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;

    private final Map<ConsumerRecord<byte[], byte[]>, ConsumerRecordWrapper<K, V>> records = new IdentityHashMap<>();

    public ConsumerRecordsWrapper(ConsumerRecords<byte[], byte[]> realRecords, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
        super(Map.of());
        this.realRecords = realRecords;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public List<ConsumerRecord<K, V>> records(TopicPartition partition) {
        return realRecords.records(partition).stream().map(this::wrapRecord).toList();
    }

    @Override
    public Set<TopicPartition> partitions() {
        return realRecords.partitions();
    }

    @Nonnull
    @Override
    public Iterator<ConsumerRecord<K, V>> iterator() {
        var real = realRecords.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return real.hasNext();
            }

            @Override
            public ConsumerRecord<K, V> next() {
                return wrapRecord(real.next());
            }
        };
    }

    @Override
    public int count() {
        return realRecords.count();
    }

    @Override
    public boolean isEmpty() {
        return realRecords.isEmpty();
    }

    private ConsumerRecord<K, V> wrapRecord(ConsumerRecord<byte[], byte[]> record) {
        return records.computeIfAbsent(record, (r) -> new ConsumerRecordWrapper<>(r, keyDeserializer, valueDeserializer));
    }


}
