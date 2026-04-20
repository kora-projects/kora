package io.koraframework.kafka.common.producer;

import io.koraframework.common.util.TimeUtils;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Properties;

public abstract class AbstractPublisher implements GeneratedPublisher {

    protected final Logger logger;

    protected final String publisherName;
    protected final KafkaPublisherTelemetryConfig telemetryConfig;
    protected final Properties driverProperties;
    protected final KafkaPublisherTelemetry telemetry;

    protected volatile Producer<byte[], byte[]> delegate;
    protected volatile KafkaClientMetrics micrometerMetrics;

    protected AbstractPublisher(String publisherName, String publisherImpl, Properties driverProperties, KafkaPublisherTelemetryConfig telemetryConfig, KafkaPublisherTelemetry telemetry) {
        this.publisherName = publisherName;
        this.telemetryConfig = telemetryConfig;
        this.driverProperties = driverProperties;
        this.telemetry = telemetry;
        var logger = LoggerFactory.getLogger(publisherImpl);
        this.logger = this.telemetryConfig.logging().enabled() && logger.isInfoEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
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
        try {
            logger.atDebug()
                .addKeyValue("publisherName", this.publisherName)
                .log("KafkaPublisher starting...");
            final long started = TimeUtils.started();

            var producer = new KafkaProducer<>(driverProperties, new ByteArraySerializer(), new ByteArraySerializer());
            this.delegate = producer;
            if (this.telemetryConfig.metrics().driverMetrics()) {
                this.micrometerMetrics = new KafkaClientMetrics(producer);
                this.micrometerMetrics.bindTo(this.telemetry.meterRegistry());
            }

            logger.atInfo()
                .addKeyValue("publisherName", this.publisherName)
                .log("KafkaPublisher started in {}", TimeUtils.tookForLogging(started));
        } catch (Exception e) {
            throw new RuntimeException("KafkaPublisher '" + publisherName + "' failed to start, due to: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() throws Exception {
        if (this.delegate == null) {
            return;
        }

        logger.atDebug()
            .addKeyValue("publisherName", this.publisherName)
            .log("KafkaPublisher stopping...");
        final long started = TimeUtils.started();

        var delegate = this.delegate;
        var micrometerMetrics = this.micrometerMetrics;
        this.delegate = null;
        this.micrometerMetrics = null;
        try (delegate; micrometerMetrics) {}

        logger.atInfo()
            .addKeyValue("publisherName", this.publisherName)
            .log("KafkaPublisher stopped in {}", TimeUtils.tookForLogging(started));
    }
}
