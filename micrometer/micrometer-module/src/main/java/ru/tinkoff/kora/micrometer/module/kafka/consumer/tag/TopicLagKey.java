package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

public record TopicLagKey(String consumerName, String topic, int partition) {}
