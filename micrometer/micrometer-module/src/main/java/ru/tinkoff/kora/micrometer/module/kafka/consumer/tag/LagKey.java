package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

public record LagKey(String consumerName, String topic, int partition) {}
