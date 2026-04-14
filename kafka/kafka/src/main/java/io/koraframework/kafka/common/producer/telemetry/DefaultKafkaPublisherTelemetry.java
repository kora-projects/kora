package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.micrometer.api.NoopCounterMeterProvider;
import io.koraframework.micrometer.api.NoopTimerMeterProvider;
import io.micrometer.core.instrument.*;
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
import java.util.List;
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

//        CachedMeterBuilder<Timer> recordDurationBuilder = new CachedMeterBuilder<>(
//            _ -> createMetricRecordDuration().register(meterRegistry),
//            Tags.of(createMetricRecordDurationStaticTags())
//        );

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
        return new DefaultKafkaPublisherRecordObservation(publisherName, config, topic, span, duration, sentMessages, logger);
    }

    protected Timer.Builder createMetricRecordDuration() {
        return Timer.builder("messaging.client.operation.duration")
            .serviceLevelObjectives(this.config.metrics().slo());
    }

    protected List<Tag> createMetricRecordDurationStaticTags() {
        var staticTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return staticTags;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Meter.MeterProvider<Timer> createMetricRecordDurationBuilder(String topic) {
        if (!this.config.metrics().enabled()) {
            return NoopTimerMeterProvider.INSTANCE;
        }

        return keyTags -> {
            // cause if exception then no metadata for topic so add here 100%
            final Tag topicDynamicTag = Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic);
            final Tags finalKeyTags;
            if (keyTags instanceof ArrayList ta) {
                ta.add(topicDynamicTag);
                finalKeyTags = Tags.of(ta);
            } else {
                finalKeyTags = Tags.of(keyTags).and(topicDynamicTag);
            }

            return recordDurationCache.computeIfAbsent(finalKeyTags, _ -> {
                // static tags are not part of cache key cause not change
                var staticTags = createMetricRecordDurationStaticTags();
                var metricBuilder = createMetricRecordDuration();
                return metricBuilder
                    .tags(staticTags)
                    .withRegistry(this.meterRegistry)
                    .withTags(finalKeyTags); // provider accept only dynamic metric cache key tags
            });
        };
    }

    protected Counter.Builder createMetricSentCounter() {
        return Counter.builder("messaging.client.sent.messages");
    }

    protected List<Tag> createMetricSentCounterStaticTags() {
        var staticTags = new ArrayList<Tag>(4 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), clientId));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_IMPL, publisherImpl));
        staticTags.add(Tag.of(MESSAGING_KAFKA_PRODUCER_NAME, publisherName));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }
        return staticTags;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Meter.MeterProvider<Counter> createMetricSentCounterBuilder(String topic) {
        if (!this.config.metrics().enabled()) {
            return NoopCounterMeterProvider.INSTANCE;
        }

        return keyTags -> {
            // cause if exception then no metadata for topic so add here 100%
            final Tag topicDynamicTag = Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic);
            final Tags finalKeyTags;
            if (keyTags instanceof ArrayList ta) {
                ta.add(topicDynamicTag);
                finalKeyTags = Tags.of(ta);
            } else {
                finalKeyTags = Tags.of(keyTags).and(topicDynamicTag);
            }

            return sentMessagesCache.computeIfAbsent(finalKeyTags, _ -> {
                // static tags are not part of cache key cause not change
                var staticTags = createMetricSentCounterStaticTags();
                var metricBuilder = createMetricSentCounter();
                return metricBuilder
                    .tags(staticTags)
                    .withRegistry(this.meterRegistry)
                    .withTags(finalKeyTags); // provider accept only dynamic metric cache key tags
            });
        };
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
