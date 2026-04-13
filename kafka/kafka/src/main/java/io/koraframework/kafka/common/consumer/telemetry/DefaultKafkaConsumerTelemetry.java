package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.micrometer.api.DefaultMeterBuilder;
import io.koraframework.micrometer.api.MeterBuilder;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultKafkaConsumerTelemetry implements KafkaConsumerTelemetry {

    private static final String MESSAGING_KAFKA_CONSUMER_NAME = "messaging.kafka.consumer.name";
    private static final String MESSAGING_KAFKA_CONSUMER_IMPL = "messaging.kafka.consumer.impl";

    private final Map<Tags, Timer> batchDurationCache = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> recordDurationCache = new ConcurrentHashMap<>();
    private final Map<Tags, AtomicLong> lagCache = new ConcurrentHashMap<>();

    protected final Logger logger;
    protected final KafkaConsumerTelemetryConfig config;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final ConsumerMetadata consumerMetadata;
    protected final Properties driverProperties;

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
        consumerMetadata = new ConsumerMetadata(
            listenerName,
            listenerImpl,
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, ""),
            driverProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG, "")
        );
        var logger = LoggerFactory.getLogger(listenerImpl);
        this.logger = this.config.logging().enabled() && logger.isWarnEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
    }

    public record ConsumerMetadata(String listenerName, String listenerImpl, String clientId, String groupId) {}

    @Override
    public MeterRegistry meterRegistry() {
        return this.meterRegistry;
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        var span = this.config.tracing().enabled()
            ? this.createSpanPoll().startSpan()
            : Span.getInvalid();
        var durationBuilder = this.createMetricBatchDurationBuilder();

        return new DefaultKafkaConsumerPollObservation(config, tracer, meterRegistry, span, durationBuilder, recordDurationCache, consumerMetadata);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        var counter = new AtomicLong();
        var meterBuilder = createMetricLagBuilder(partition, counter);

        this.lagCache.computeIfAbsent(meterBuilder.getTags(), _ -> {
            meterBuilder.build();
            return counter;
        }).set(lag);
    }

    protected Timer.Builder createMetricBatchDuration() {
        return Timer.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.metrics().slo());
    }

    protected MeterBuilder<Timer> createMetricBatchDurationBuilder() {
        var baseTags = new ArrayList<Tag>(5 + this.config.metrics().tags().size());
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
        for (var e : this.config.metrics().tags().entrySet()) {
            baseTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        var meterBuilder = new DefaultMeterBuilder<>(tags -> batchDurationCache.computeIfAbsent(tags, _ -> {
            var builder = createMetricBatchDuration();
            var provider = builder.withRegistry(this.meterRegistry);
            return provider.withTags(tags);
        }));
        meterBuilder.tags(baseTags);
        return meterBuilder;
    }

    protected MeterBuilder<Gauge> createMetricLagBuilder(TopicPartition partition, AtomicLong counter) {
        var baseTags = new ArrayList<Tag>(7 + this.config.metrics().tags().size());
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(partition.partition())));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
        for (var e : this.config.metrics().tags().entrySet()) {
            baseTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        var meterBuilder = new DefaultMeterBuilder<>(tags -> {
            var builder = Gauge.builder("messaging.kafka.consumer.lag", counter, AtomicLong::get);
            return builder
                .tags(tags)
                .register(meterRegistry);
        });
        meterBuilder.tags(baseTags);
        return meterBuilder;
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
