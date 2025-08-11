package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import jakarta.annotation.Nullable;

public record RecordDurationKey(String consumerName, String topic, int partition, @Nullable Class<? extends Throwable> errorType) {}
