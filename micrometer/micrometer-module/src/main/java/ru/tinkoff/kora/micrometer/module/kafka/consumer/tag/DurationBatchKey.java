package ru.tinkoff.kora.micrometer.module.kafka.consumer.tag;

import jakarta.annotation.Nullable;

public record DurationBatchKey(String consumerName, @Nullable Class<? extends Throwable> errorType) {}
