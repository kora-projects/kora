package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultKafkaPublisherTelemetry implements KafkaPublisherTelemetry {
    private static final Meter.Id emptyTimerId = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.TIMER);
    private static final Meter.Id emptyCounterId = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.COUNTER);
    protected final KafkaPublisherTelemetryConfig config;
    protected final Tracer tracer;
    protected final Logger logger;
    protected final String clientId;
    protected MeterRegistry meterRegistry;

    protected ConcurrentHashMap<String, ConcurrentHashMap<Iterable<? extends Tag>, Timer>> durationCache;
    protected ConcurrentHashMap<String, ConcurrentHashMap<Iterable<? extends Tag>, Counter>> sentMessagesCache;

    public DefaultKafkaPublisherTelemetry(String publisherName, KafkaPublisherTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, Properties driverProperties) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.logger = LoggerFactory.getLogger("ru.tinkoff.kora.kafka.publisher." + publisherName);
        this.clientId = (driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG) instanceof String s) ? s : "";
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.meterRegistry;
    }

    @Override
    public KafkaPublisherTransactionObservation observeTx() {
        var span = this.createTxSpan();
        var logger = this.config.logging().enabled() ? this.logger : NOPLogger.NOP_LOGGER;

        return new DefaultKafkaPublisherTransactionObservation(span, logger);
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        var span = this.createSendSpan(topic);
        var duration = this.duration(topic);
        var sentMessages = this.sentMessages(topic);
        var logger = this.config.logging().enabled() ? this.logger : NOPLogger.NOP_LOGGER;
        return new DefaultKafkaPublisherRecordObservation(span, duration, sentMessages, logger);
    }

    protected Meter.MeterProvider<Timer> duration(String topic) {
        if (!this.config.metrics().enabled()) {
            return _ -> new NoopTimer(emptyTimerId);
        }
        var b = Timer.builder("messaging.client.operation.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND);
        var tags = new ArrayList<Tag>(3 + this.config.metrics().tags().size());
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        for (var entry : this.config.metrics().tags().entrySet()) {
            tags.add(Tag.of(entry.getKey(), entry.getValue()));
        }
        var provider = b.tags(tags).withRegistry(this.meterRegistry);
        var durationCache = this.durationCache.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
        return additionalTags -> durationCache.computeIfAbsent(additionalTags, provider::withTags);
    }

    protected Meter.MeterProvider<Counter> sentMessages(String topic) {
        if (!this.config.metrics().enabled()) {
            return _ -> new NoopCounter(emptyCounterId);
        }
        var b = Counter.builder("messaging.client.sent.messages");
        var tags = new ArrayList<Tag>(3 + this.config.metrics().tags().size());
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        for (var entry : this.config.metrics().tags().entrySet()) {
            tags.add(Tag.of(entry.getKey(), entry.getValue()));
        }
        var provider = b.tags(tags).withRegistry(this.meterRegistry);
        var durationCache = this.sentMessagesCache.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
        return additionalTags -> durationCache.computeIfAbsent(additionalTags, provider::withTags);
    }

    protected Span createSendSpan(String topic) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var b = this.tracer.spanBuilder(topic + " send")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topic);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }


    protected Span createTxSpan() {
        var b = this.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka");
        for (var entry : this.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b.startSpan();
    }

}
