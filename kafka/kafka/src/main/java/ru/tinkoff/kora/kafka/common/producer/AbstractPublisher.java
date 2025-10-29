package ru.tinkoff.kora.kafka.common.producer;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;

import java.util.Properties;

public abstract class AbstractPublisher implements GeneratedPublisher {
    protected final KafkaPublisherTelemetryConfig telemetryConfig;
    protected final Properties driverProperties;
    protected final KafkaPublisherTelemetry telemetry;

    protected volatile Producer<byte[], byte[]> delegate;
    protected volatile KafkaClientMetrics micrometerMetrics;

    protected AbstractPublisher(Properties driverProperties, KafkaPublisherTelemetryConfig telemetryConfig, KafkaPublisherTelemetry telemetry) {
        this.telemetryConfig = telemetryConfig;
        this.driverProperties = driverProperties;
        this.telemetry = telemetry;
    }


    @Override
    public Producer<byte[], byte[]> producer() {
        return this.delegate;
    }

    @Override
    public KafkaPublisherTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public void init() throws Exception {
        if (this.delegate != null) {
            return;
        }
        var producer = new KafkaProducer<>(driverProperties, new ByteArraySerializer(), new ByteArraySerializer());
        this.delegate = producer;
        if (this.telemetryConfig.metrics().driverMetrics()) {
            this.micrometerMetrics = new KafkaClientMetrics(producer);
            this.micrometerMetrics.bindTo(this.telemetry.meterRegistry());
        }
    }

    @Override
    public void release() throws Exception {
        if (this.delegate == null) {
            return;
        }
        var delegate = this.delegate;
        var micrometerMetrics = this.micrometerMetrics;
        this.delegate = null;
        this.micrometerMetrics = null;
        try (delegate; micrometerMetrics) {}
    }
}
