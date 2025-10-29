package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaConsumerPollObservation implements KafkaConsumerPollObservation {
    private final KafkaConsumerTelemetryConfig config;
    private final ConcurrentHashMap<Tags, Timer> recordDurationCache;
    private final Tracer tracer;
    private final Meter.MeterProvider<Timer> duration;
    private final MeterRegistry meterRegistry;
    private final Span span;

    private final Logger logger;
    private final DefaultKafkaConsumerTelemetry.ConsumerMetadata consumerMetadata;
    private Throwable error;
    private long recordsTime;

    public DefaultKafkaConsumerPollObservation(KafkaConsumerTelemetryConfig config, ConcurrentHashMap<Tags, Timer> recordDurationCache, Tracer tracer, MeterRegistry meterRegistry, Span span, Meter.MeterProvider<Timer> duration, DefaultKafkaConsumerTelemetry.ConsumerMetadata consumerMetadata) {
        this.config = config;
        this.recordDurationCache = recordDurationCache;
        this.meterRegistry = meterRegistry;
        this.span = span;
        this.tracer = tracer;
        this.duration = duration;
        this.consumerMetadata = consumerMetadata;
        this.logger = LoggerFactory.getLogger(consumerMetadata.consumerName());
    }

    @Override
    public void observeRecords(ConsumerRecords<?, ?> records) {
        span.setAttribute(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, (long) records.count());
        if (this.config.logging().enabled()) {
            if (logger.isTraceEnabled()) {
                if (records.isEmpty()) {
                    logger.trace("Kafka Consumer '{}' polled '0' records", consumerMetadata.consumerName());
                } else {
                    var logTopics = new HashSet<String>(records.partitions().size());
                    var logPartitions = new HashSet<Integer>(records.partitions().size());
                    for (TopicPartition partition : records.partitions()) {
                        logPartitions.add(partition.partition());
                        logTopics.add(partition.topic());
                    }

                    logger.trace("Kafka Consumer '{}' polled '{}' records from topics {} and partitions {}",
                        consumerMetadata.consumerName(), records.count(), logTopics, logPartitions);
                }
            } else {
                if (!records.isEmpty()) {
                    logger.debug("Kafka Consumer '{}' polled '{}' records", consumerMetadata.consumerName(), records.count());
                }
            }
        }
        this.recordsTime = System.nanoTime();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        var took = System.nanoTime() - this.recordsTime;
        this.span.addEvent("result");
        this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), error == null ? "" : error.getClass().getCanonicalName())
            .record(took, TimeUnit.NANOSECONDS);
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
        var duration = this.createRecordDuration(record);

        return new DefaultKafkaConsumerRecordObservation(span, duration);
    }

    protected Meter.MeterProvider<Timer> createRecordDuration(ConsumerRecord<?, ?> record) {
        var builder = Timer.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), consumerMetadata.clientId())
            .tag(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), consumerMetadata.groupId())
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), record.topic())
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Integer.toString(record.partition()))
            .tag("messaging.kafka.consumer.name", consumerMetadata.consumerName());
        for (var e : this.config.metrics().tags().entrySet()) {
            builder.tag(e.getKey(), e.getValue());
        }
        var provider = builder.withRegistry(this.meterRegistry);
        return tags -> recordDurationCache.computeIfAbsent(Tags.of(tags), provider::withTags);
    }

    protected Span createSpan(ConsumerRecord<?, ?> record) {
        var root = io.opentelemetry.context.Context.root();
        var parent = W3CTraceContextPropagator.getInstance().extract(root, record, ConsumerRecordTextMapGetter.INSTANCE);

        var recordSpanBuilder = this.tracer
            .spanBuilder(record.topic() + " process")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(parent)
            .addLink(span.getSpanContext())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()))
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, record.offset());
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
