package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import jakarta.annotation.Nullable;

public record RecordsDurationKey(String consumerName, @Nullable Class<? extends Throwable> errorType) {}
