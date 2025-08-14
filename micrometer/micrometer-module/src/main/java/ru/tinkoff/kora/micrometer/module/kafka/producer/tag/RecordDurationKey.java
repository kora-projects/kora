package ru.tinkoff.kora.micrometer.module.kafka.producer.tag;

import jakarta.annotation.Nullable;

public record RecordDurationKey(String topic, int partition, @Nullable Class<? extends Throwable> errorType) {}
