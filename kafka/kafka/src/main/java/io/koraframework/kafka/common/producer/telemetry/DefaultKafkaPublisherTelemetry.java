package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.micrometer.api.NoopCounterMeterProvider;
import io.koraframework.micrometer.api.NoopTimerMeterProvider;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
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
    protected final MeterRegistry meterRegistry;

    protected final Map<Tags, Timer> recordDurationCache = new ConcurrentHashMap<>();
    protected final Map<Tags, Counter> sentMessagesCache = new ConcurrentHashMap<>();
    protected final Meter.MeterProvider<Timer> recordDurationMeter;
    protected final Meter.MeterProvider<Counter> sentMessagesMeter;

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

        this.recordDurationMeter = (config.metrics().enabled())
            ? tags -> recordDurationCache.computeIfAbsent(Tags.of(tags), t -> createMetricRecordDuration()
            .tags((Iterable<Tag>) tags)
            .register(meterRegistry))
            : NoopTimerMeterProvider.INSTANCE;

        this.sentMessagesMeter = (config.metrics().enabled())
            ? tags -> sentMessagesCache.computeIfAbsent(Tags.of(tags), t -> createMetricSentCounter()
            .tags((Iterable<Tag>) tags)
            .register(meterRegistry))
            : NoopCounterMeterProvider.INSTANCE;

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
        var span = this.config.tracing().enabled()
            ? createTxSpan().startSpan()
            : Span.getInvalid();
        return new DefaultKafkaPublisherTransactionObservation(publisherName, span, logger);
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        var span = this.config.tracing().enabled()
            ? createSendSpan(topic).startSpan()
            : Span.getInvalid();
        return new DefaultKafkaPublisherRecordObservation(publisherName, config, topic, span, recordDurationMeter, sentMessagesMeter, logger);
    }

    protected Timer.Builder createMetricRecordDuration() {
        var staticTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return Timer.builder("messaging.client.operation.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tags(staticTags);
    }

    protected Counter.Builder createMetricSentCounter() {
        var staticTags = new ArrayList<Tag>(4 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return Counter.builder("messaging.client.sent.messages")
            .tags(staticTags);
    }

    protected SpanBuilder createSendSpan(String topic) {
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

        return b;
    }

    protected SpanBuilder createTxSpan() {
        var b = this.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl)
            .setAttribute(MESSAGING_KAFKA_PRODUCER_NAME, publisherName);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b;
    }
}
