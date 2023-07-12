package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetricsFactory;

import javax.annotation.Nullable;
import java.util.Properties;

public class MicrometerKafkaProducerMetricsFactory implements KafkaProducerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public KafkaProducerMetrics get(Producer<?, ?> producer, Properties properties) {
        return new MicrometerKafkaProducerMetrics(meterRegistry, producer);
    }
}
