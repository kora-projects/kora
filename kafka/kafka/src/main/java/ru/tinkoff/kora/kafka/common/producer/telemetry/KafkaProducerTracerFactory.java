package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaProducerTracerFactory {
    @Nullable
    KafkaProducerTracer get(TelemetryConfig.TracingConfig config, Producer<?, ?> producer, Properties properties);
}
