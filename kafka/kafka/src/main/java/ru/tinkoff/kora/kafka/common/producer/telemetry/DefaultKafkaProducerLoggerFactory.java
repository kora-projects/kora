package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public class DefaultKafkaProducerLoggerFactory implements KafkaProducerLoggerFactory {
    @Override
    @Nullable
    public KafkaProducerLogger get(TelemetryConfig.LogConfig logging, Producer<?, ?> producer, Properties properties) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultKafkaProducerLogger();
        } else {
            return null;
        }
    }
}
