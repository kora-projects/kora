package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.micrometer.api.DefaultMeterBuilder;
import io.koraframework.micrometer.api.MeterBuilder;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaConsumerPollObservation implements KafkaConsumerPollObservation {

    private static final String MESSAGING_KAFKA_CONSUMER_NAME = "messaging.kafka.consumer.name";
    private static final String MESSAGING_KAFKA_CONSUMER_IMPL = "messaging.kafka.consumer.impl";

    protected final Logger logger;
    protected final KafkaConsumerTelemetryConfig config;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final Span span;
    protected final MeterBuilder<Timer> batchMetricBuilder;
    protected final DefaultKafkaConsumerTelemetry.ConsumerMetadata consumerMetadata;

    private final Map<Tags, Timer> recordDurationCache;

    private Throwable error;
    private long recordsTime;

    public DefaultKafkaConsumerPollObservation(KafkaConsumerTelemetryConfig config,
                                               Tracer tracer,
                                               MeterRegistry meterRegistry,
                                               Span span,
                                               MeterBuilder<Timer> batchMetricBuilder,
                                               Map<Tags, Timer> recordDurationCache,
                                               DefaultKafkaConsumerTelemetry.ConsumerMetadata consumerMetadata) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.span = span;
        this.batchMetricBuilder = batchMetricBuilder;
        this.consumerMetadata = consumerMetadata;
        this.recordDurationCache = recordDurationCache;
        this.logger = LoggerFactory.getLogger(consumerMetadata.listenerImpl());
    }

    @Override
    public void observeRecords(ConsumerRecords<?, ?> records) {
        if (this.config.logging().enabled()) {
            if (logger.isTraceEnabled()) {
                if (records.isEmpty()) {
                    logger.trace("KafkaListener '{}' polled '0' records");
                } else {
                    var logTopics = new HashSet<String>(records.partitions().size());
                    var logPartitions = new HashSet<Integer>(records.partitions().size());
                    for (TopicPartition partition : records.partitions()) {
                        logPartitions.add(partition.partition());
                        logTopics.add(partition.topic());
                    }

                    logger.trace("KafkaListener '{}' polled '{}' records from topics {} and partitions {}",
                        consumerMetadata.listenerImpl(), records.count(), logTopics, logPartitions);
                }
            } else {
                if (!records.isEmpty()) {
                    logger.debug("KafkaListener '{}' polled '{}' records", records.count());
                }
            }
        }

        this.span.setAttribute(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, (long) records.count());
        this.recordsTime = System.nanoTime();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var took = System.nanoTime() - this.recordsTime;

        var errorValue = error == null ? "" : error.getClass().getCanonicalName();
        var timer = this.batchMetricBuilder.tag(ErrorAttributes.ERROR_TYPE.getKey(), errorValue).build();
        timer.record(took, TimeUnit.NANOSECONDS);

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.addEvent("result");
        this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record) {
        var span = this.createSpan(record);
        var durationBuilder = this.createMetricRecordDurationBuilder(record);

        return new DefaultKafkaConsumerRecordObservation(span, durationBuilder);
    }

    protected Timer.Builder createMetricRecordDuration() {
        return Timer.builder("messaging.receive.duration")
            .serviceLevelObjectives(this.config.metrics().slo());
    }

    protected MeterBuilder<Timer> createMetricRecordDurationBuilder(ConsumerRecord<?, ?> record) {
        var baseTags = new ArrayList<Tag>(7 + this.config.metrics().tags().size());
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), record.topic()));
        baseTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(record.partition())));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl()));
        baseTags.add(Tag.of(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName()));
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

    protected Span createSpan(ConsumerRecord<?, ?> record) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }

        var root = io.opentelemetry.context.Context.root();
        var parent = W3CTraceContextPropagator.getInstance().extract(root, record, ConsumerRecordTextMapGetter.INSTANCE);

        var recordSpanBuilder = this.tracer
            .spanBuilder(record.topic() + " process record")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(parent)
            .addLink(span.getSpanContext())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()))
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, record.offset())
            .setAttribute(MESSAGING_KAFKA_CONSUMER_NAME, consumerMetadata.listenerName())
            .setAttribute(MESSAGING_KAFKA_CONSUMER_IMPL, consumerMetadata.listenerImpl());
        try {
            recordSpanBuilder.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, Objects.toString(record.key()));
        } catch (Exception ignore) {}
        var span = recordSpanBuilder.startSpan();
        this.span.addLink(span.getSpanContext());

        return span;
    }

    private enum ConsumerRecordTextMapGetter implements TextMapGetter<ConsumerRecord<?, ?>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
            var set = new HashSet<String>();
            for (var header : carrier.headers()) {
                set.add(header.key());
            }
            return set;

        }

        @Override
        public String get(ConsumerRecord<?, ?> carrier, String key) {
            var header = carrier.headers().lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }
}
