package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.micrometer.api.NoopTimerMeterProvider;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultKafkaConsumerTelemetry implements KafkaConsumerTelemetry {

    private static final String MESSAGING_KAFKA_CONSUMER_NAME = "messaging.kafka.consumer.name";
    private static final String MESSAGING_KAFKA_CONSUMER_IMPL = "messaging.kafka.consumer.impl";

    protected final Logger logger;
    protected final KafkaConsumerTelemetryConfig config;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final ConsumerMetadata consumerMetadata;
    protected final Properties driverProperties;

    protected final Map<Tags, Timer> batchDurationCache = new ConcurrentHashMap<>();
    protected final Map<Tags, Timer> recordDurationCache = new ConcurrentHashMap<>();
    protected final Map<Tags, AtomicLong> lagGaugeCache = new ConcurrentHashMap<>();
    protected final Meter.MeterProvider<Timer> batchDurationMeter;
    protected final Meter.MeterProvider<Timer> recordDurationMeter;

    public DefaultKafkaConsumerTelemetry(KafkaConsumerTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         String listenerName,
                                         String listenerImpl,
                                         Properties driverProperties) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.driverProperties = driverProperties;
        this.consumerMetadata = new ConsumerMetadata(
                listenerName,
                listenerImpl,
                driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, ""),
                driverProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG, "")
        );

        this.batchDurationMeter = (config.metrics().enabled())
                ? tags -> batchDurationCache.computeIfAbsent(Tags.of(tags), _ -> createMetricBatchDuration()
                .tags((Iterable<Tag>) tags)
                .register(meterRegistry))
                : NoopTimerMeterProvider.INSTANCE;

        this.recordDurationMeter = (config.metrics().enabled())
                ? tags -> recordDurationCache.computeIfAbsent(Tags.of(tags), _ -> createMetricRecordDuration()
                .tags((Iterable<Tag>) tags)
                .register(meterRegistry))
                : NoopTimerMeterProvider.INSTANCE;

        var logger = LoggerFactory.getLogger(listenerImpl);
        this.logger = this.config.logging().enabled() && logger.isWarnEnabled()
                ? logger
                : NOPLogger.NOP_LOGGER;
    }

    public record ConsumerMetadata(String listenerName, String listenerImpl, String clientId, String groupId) {
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.meterRegistry;
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        var span = this.config.tracing().enabled()
                ? this.createSpanPoll().startSpan()
                : Span.getInvalid();

        return new DefaultKafkaConsumerPollObservation(consumerMetadata, config, meterRegistry, tracer, span, batchDurationMeter, recordDurationMeter);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        if (!this.config.metrics().enabled()) {
            return;
        }

        var keyTags = Tags.of(
                Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic()),
                Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(partition.partition()))
        );

        var lagCounter = this.lagGaugeCache.computeIfAbsent(keyTags, _ -> {
            var counter = new AtomicLong();
            Gauge.builder("messaging.kafka.consumer.lag", counter, AtomicLong::get)
                    .tags(createMetricLagStaticTags())
                    .register(meterRegistry);
            return counter;
        });
        lagCounter.set(lag);
    }

    protected List<Tag> createMetricLagStaticTags() {
        var staticTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }
        return staticTags;
    }

    protected Timer.Builder createMetricBatchDuration() {
        var meter = Timer.builder("messaging.process.batch.duration")
                .serviceLevelObjectives(this.config.metrics().slo());

        var staticTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return meter.tags(staticTags);
    }

    protected Timer.Builder createMetricRecordDuration() {
        var meter = Timer.builder("messaging.receive.duration")
                .serviceLevelObjectives(this.config.metrics().slo());

        var staticTags = new ArrayList<Tag>(7 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        staticTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return meter.tags(staticTags);
    }

    protected SpanBuilder createSpanPoll() {
        var span = this.tracer.spanBuilder("kafka.poll")
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId())
                .setAttribute(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName())
                .setAttribute(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl())
                .setNoParent();
        for (var e : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }
        return span;
    }
}
