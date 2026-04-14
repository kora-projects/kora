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
        if(!this.config.metrics().enabled()) {
            return;
        }

        var dynamicMetricCacheKeyTags = Tags.of(
            Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic()),
            Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(partition.partition()))
        );

        var lagCounter = this.lagCache.computeIfAbsent(dynamicMetricCacheKeyTags, _ -> {
            // static tags are not part of cache key cause not change
            var staticTags = createMetricLagStaticTags();
            var counter = new AtomicLong();
            Gauge.builder("messaging.kafka.consumer.lag", counter, AtomicLong::get)
                .tags(staticTags)
                .tags(dynamicMetricCacheKeyTags) // provider accept only dynamic metric cache key tags
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
        return Timer.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.metrics().slo());
    }

    protected List<Tag> createMetricBatchDurationStaticTags() {
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

    protected Meter.MeterProvider<Timer> createMetricBatchDurationBuilder() {
        if (!this.config.metrics().enabled()) {
            return NoopTimerMeterProvider.INSTANCE;
        }

        return keyTags -> batchDurationCache.computeIfAbsent(Tags.of(keyTags), _ -> {
            // static tags are not part of cache key cause not change
            var staticTags = createMetricBatchDurationStaticTags();
            var metricBuilder = createMetricBatchDuration();
            return metricBuilder
                .tags(staticTags)
                .withRegistry(this.meterRegistry)
                .withTags(keyTags); // provider accept only dynamic metric cache key tags
        });
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
