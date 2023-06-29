package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.producer.Producer;

public interface TransactionalProducer<K, V> {
    Producer<K, V> begin();
}
