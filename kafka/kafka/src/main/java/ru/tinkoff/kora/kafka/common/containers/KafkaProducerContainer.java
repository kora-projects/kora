package ru.tinkoff.kora.kafka.common.containers;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serializer;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.Properties;

public final class KafkaProducerContainer<K,V> implements Lifecycle, Wrapped<KafkaProducer<K,V>> {
    private KafkaProducer<K, V> producer;
    private final Properties properties;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;

    public KafkaProducer<K, V> producer() {
        return this.producer;
    }

    public KafkaProducerContainer(Properties properties, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.properties = properties;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @Override
    public Mono<Void> init() {
        return Mono.fromRunnable(() -> producer = new KafkaProducer<>(properties, keySerializer, valueSerializer));
    }

    @Override
    public Mono<Void> release() {
        return Mono.fromRunnable(() -> producer.close());
    }

    @Override
    public KafkaProducer<K, V> value() {
        return producer;
    }
}
