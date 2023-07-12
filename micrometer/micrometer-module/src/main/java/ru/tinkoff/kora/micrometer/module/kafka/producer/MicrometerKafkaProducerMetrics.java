package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;

import javax.annotation.Nullable;

public class MicrometerKafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {
    private final KafkaClientMetrics metrics;

    public MicrometerKafkaProducerMetrics(MeterRegistry meterRegistry, Producer<?, ?> producer) {
        this.metrics = new KafkaClientMetrics(producer);
        this.metrics.bindTo(meterRegistry);
    }

    @Override
    public KafkaProducerTxMetrics tx() {
        return new KafkaProducerTxMetrics() {
            @Override
            public void commit() {

            }

            @Override
            public void rollback(@Nullable Throwable e) {

            }
        };
    }

    @Override
    public void close() throws Exception {
        this.metrics.close();
    }
}
