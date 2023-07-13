package ru.tinkoff.kora.kafka.common.producer;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Properties;

@ConfigValueExtractor
public interface PublisherConfig {
    Properties driverProperties();

    @Nullable
    TransactionConfig transaction();

    @ConfigValueExtractor
    interface TransactionConfig {
        String idPrefix();

        default int maxPoolSize() {
            return 10;
        }

        default Duration maxWaitTime() {
            return Duration.ofSeconds(10);
        }
    }
}
