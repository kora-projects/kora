package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultKafkaConsumerTelemetry implements KafkaConsumerTelemetry {
    private final KafkaConsumerTelemetryConfig config;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Tags, Timer> batchDurationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Tags, Timer> recordDurationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Tags, AtomicLong> lagCache = new ConcurrentHashMap<>();
    private final ConsumerMetadata consumerMetadata;

    public DefaultKafkaConsumerTelemetry(KafkaConsumerTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, String consumerName, Properties driverProperties) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.consumerMetadata = new ConsumerMetadata(
            consumerName,
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, ""),
            driverProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG, "")
        );
    }

    public record ConsumerMetadata(String consumerName, String clientId, String groupId) {}

    @Override
    public MeterRegistry meterRegistry() {
        return this.meterRegistry;
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        var span = this.createSpan();
        var duration = this.createDuration();

        return new DefaultKafkaConsumerPollObservation(this.config, this.recordDurationCache, this.tracer, this.meterRegistry, span, duration, this.consumerMetadata);
    }

    protected Meter.MeterProvider<Timer> createDuration() {
        var builder = Timer.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), this.consumerMetadata.clientId())
            .tag(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), this.consumerMetadata.groupId())
            .tag("messaging.kafka.consumer.name", consumerMetadata.consumerName());
        for (var e : this.config.metrics().tags().entrySet()) {
            builder.tag(e.getKey(), e.getValue());
        }
        var provider = builder.withRegistry(this.meterRegistry);
        return tags -> batchDurationCache.computeIfAbsent(Tags.of(tags), provider::withTags);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        var tags = new ArrayList<Tag>(7);
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic()));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(partition.partition())));
        tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), this.consumerMetadata.clientId()));
        tags.add(Tag.of("messaging.kafka.consumer.name", this.consumerMetadata.consumerName()));

        this.lagCache.computeIfAbsent(Tags.of(tags), t -> {
            var l = new AtomicLong(0);
            Gauge.builder("messaging.kafka.consumer.lag", l, AtomicLong::get)
                .tags(t)
                .register(meterRegistry);
            return l;
        }).set(lag);
    }

    protected Span createSpan() {
        var span = this.tracer.spanBuilder("kafka.poll")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .setNoParent();
        for (var e : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }
        return span.startSpan();
    }

}
