package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaProducerTelemetryFactory {

    /**
     * @see #get(String, TelemetryConfig, Producer<?, ?>, Properties)
     */
    @Deprecated
    @Nullable
    KafkaProducerTelemetry get(TelemetryConfig config, Producer<?, ?> producer, Properties properties);

    @Nullable
    default KafkaProducerTelemetry get(String producerName, TelemetryConfig config, Producer<?, ?> producer, Properties properties) {
        return get(config, producer, properties);
    }
}
