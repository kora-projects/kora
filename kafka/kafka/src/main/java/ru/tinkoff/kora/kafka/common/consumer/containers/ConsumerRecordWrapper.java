package ru.tinkoff.kora.kafka.common.consumer.containers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException;
import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ConsumerRecordWrapper<K, V> extends ConsumerRecord<K, V> {
    private final ConsumerRecord<byte[], byte[]> realRecord;
    private final Deserializer<K> keyDeserializer;
    private final Deserializer<V> valueDeserializer;

    private final AtomicReference<K> deserializedKey = new AtomicReference<>(null);
    private final AtomicReference<V> deserializedValue = new AtomicReference<>(null);

    public ConsumerRecordWrapper(ConsumerRecord<byte[], byte[]> realRecord, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {
        super(realRecord.topic(), realRecord.partition(), realRecord.offset(), null, null);
        this.realRecord = realRecord;
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public String topic() {
        return realRecord.topic();
    }

    @Override
    public int partition() {
        return realRecord.partition();
    }

    @Override
    public Headers headers() {
        return realRecord.headers();
    }

    @Override
    public K key() {
        var value = deserializedKey.get();
        if (value == null) {
            try {
                value = keyDeserializer.deserialize(realRecord.topic(), realRecord.headers(), realRecord.key());
            } catch (Exception e) {
                throw new RecordKeyDeserializationException(e, realRecord);
            }
            deserializedKey.set(value);
        }
        return value;
    }

    @Override
    public V value() {
        var value = deserializedValue.get();
        if (value == null) {
            try {
                value = valueDeserializer.deserialize(realRecord.topic(), realRecord.headers(), realRecord.value());
            } catch (Exception e) {
                throw new RecordValueDeserializationException(e, realRecord);
            }
            deserializedValue.set(value);
        }
        return value;
    }

    @Override
    public long offset() {
        return realRecord.offset();
    }

    @Override
    public long timestamp() {
        return realRecord.timestamp();
    }

    @Override
    public TimestampType timestampType() {
        return realRecord.timestampType();
    }

    @Override
    public int serializedKeySize() {
        return realRecord.serializedKeySize();
    }

    @Override
    public int serializedValueSize() {
        return realRecord.serializedValueSize();
    }

    @Override
    public Optional<Integer> leaderEpoch() {
        return realRecord.leaderEpoch();
    }

    @Override
    public String toString() {
        return realRecord.toString();
    }
}
