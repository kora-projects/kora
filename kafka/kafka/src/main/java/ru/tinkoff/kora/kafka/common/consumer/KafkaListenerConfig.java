package ru.tinkoff.kora.kafka.common.consumer;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@ConfigValueExtractor
public interface KafkaListenerConfig {

    Properties driverProperties();

    @Nullable
    List<String> topics();

    @Nullable
    Pattern topicsPattern();

    @Nullable
    List<String> partitions();

    default Either<Duration, String> offset() {
        return Either.right("latest");
    }

    default Duration pollTimeout() {
        return Duration.ofSeconds(5);
    }

    default Duration backoffTimeout() {
        return Duration.ofSeconds(15);
    }

    default int threads() {
        return 1;
    }

    default Duration partitionRefreshInterval() {
        return Duration.ofMinutes(1);
    }

    default Duration shutdownAwait() {
        return Duration.ofSeconds(30);
    }

    TelemetryConfig telemetry();

    default KafkaListenerConfig withDriverPropertiesOverrides(Map<String, Object> overrides) {
        var props = new Properties();
        props.putAll(driverProperties());
        props.putAll(overrides);
        return new $KafkaListenerConfig_ConfigValueExtractor.KafkaListenerConfig_Impl(
            props,
            topics(),
            topicsPattern(),
            partitions(),
            offset(),
            pollTimeout(),
            backoffTimeout(),
            threads(),
            partitionRefreshInterval(),
            shutdownAwait(),
            telemetry()
        );
    }
}
