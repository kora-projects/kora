package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.micrometer.api.DefaultMeterBuilder;
import io.koraframework.micrometer.api.MeterBuilder;
import io.koraframework.micrometer.api.NoopCounterMeterBuilder;
import io.koraframework.micrometer.api.NoopTimerMeterBuilder;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultKafkaPublisherTelemetry implements KafkaPublisherTelemetry {

    private static final String MESSAGING_KAFKA_PRODUCER_NAME = "messaging.kafka.producer.name";
    private static final String MESSAGING_KAFKA_PRODUCER_IMPL = "messaging.kafka.producer.impl";

    protected final KafkaPublisherTelemetryConfig config;
    protected final String publisherName;
    protected final String publisherImpl;
    protected final Tracer tracer;
    protected final Logger logger;
    protected final String clientId;
    protected MeterRegistry meterRegistry;

    private final Map<Tags, Timer> recordDurationCache = new ConcurrentHashMap<>();
    private final Map<Tags, Counter> sentMessagesCache = new ConcurrentHashMap<>();

    public DefaultKafkaPublisherTelemetry(String publisherName,
                                          String publisherImpl,
                                          KafkaPublisherTelemetryConfig config,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          Properties driverProperties) {
        this.publisherName = publisherName;
        this.publisherImpl = publisherImpl;
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.clientId = (driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG) instanceof String s) ? s : "";
        var logger = LoggerFactory.getLogger(publisherImpl);
        this.logger = this.config.logging().enabled() && logger.isWarnEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.meterRegistry;
    }

    @Override
    public KafkaPublisherTransactionObservation observeTx() {
        var span = this.createTxSpan();
        return new DefaultKafkaPublisherTransactionObservation(span, logger);
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        var span = this.createSendSpan(topic);
        var duration = this.createMetricRecordDurationBuilder(topic);
        var sentMessages = this.createMetricSentCounterBuilder(topic);
        return new DefaultKafkaPublisherRecordObservation(publisherName, span, duration, sentMessages, logger);
    }

    protected Timer.Builder createMetricRecordDuration() {
        return Timer.builder("messaging.client.operation.duration")
            .serviceLevelObjectives(this.config.metrics().slo());
    }

    protected MeterBuilder<Timer> createMetricRecordDurationBuilder(String topic) {
        if (!this.config.metrics().enabled()) {
            return NoopTimerMeterBuilder.INSTANCE;
        }

        var baseTags = new ArrayList<Tag>(6 + this.config.metrics().tags().size());
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
        baseTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        baseTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            baseTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        var meterBuilder = new DefaultMeterBuilder<>(tags -> recordDurationCache.computeIfAbsent(tags, _ -> {
            var builder = createMetricRecordDuration();
            var provider = builder.withRegistry(this.meterRegistry);
            return provider.withTags(tags);
        }));
        meterBuilder.tags(baseTags);
        return meterBuilder;
    }

    protected Counter.Builder createMetricSentCounter() {
        return Counter.builder("messaging.client.sent.messages");
    }

    protected MeterBuilder<Counter> createMetricSentCounterBuilder(String topic) {
        if (!this.config.metrics().enabled()) {
            return NoopCounterMeterBuilder.INSTANCE;
        }

        var baseTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        baseTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        baseTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            baseTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        var meterBuilder = new DefaultMeterBuilder<>(tags -> sentMessagesCache.computeIfAbsent(tags, _ -> {
            var builder = createMetricSentCounter();
            var provider = builder.withRegistry(this.meterRegistry);
            return provider.withTags(tags);
        }));
        meterBuilder.tags(baseTags);
        return meterBuilder;
    }

    protected Span createSendSpan(String topic) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }

        var b = this.tracer.spanBuilder(topic + " send")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topic)
            .setAttribute(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl)
            .setAttribute(MESSAGING_KAFKA_PRODUCER_NAME, publisherName);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }

    protected Span createTxSpan() {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }

        var b = this.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }

}
