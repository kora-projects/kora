package ru.tinkoff.kora.kafka.common.consumer.containers;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serializer;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.Properties;

/**
 * @param <K> key type
 * @param <V> value type
 * @deprecated move to @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher generated producer
 * @see ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher
 */
@Deprecated
public final class KafkaProducerContainer<K, V> implements Lifecycle, Wrapped<KafkaProducer<K, V>> {

    private KafkaProducer<K, V> producer;
    private final Properties properties;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;

    public KafkaProducerContainer(Properties properties, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.properties = properties;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @Override
    public void init() {
        producer = new KafkaProducer<>(properties, keySerializer, valueSerializer);
    }

    @Override
    public void release() {
        producer.close();
        producer = null;
    }

    public KafkaProducer<K, V> producer() {
        return this.producer;
    }

    @Override
    public KafkaProducer<K, V> value() {
        return this.producer;
    }
}
