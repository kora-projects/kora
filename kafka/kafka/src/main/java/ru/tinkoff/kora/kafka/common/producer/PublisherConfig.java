package ru.tinkoff.kora.kafka.common.producer;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Properties;

public record PublisherConfig(Properties driverProperties, @Nullable TransactionConfig transaction) {
    public record TransactionConfig(String idPrefix, int maxPoolSize, Duration maxWaitTime) {}
}
