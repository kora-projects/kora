package io.koraframework.kafka.common.producer.telemetry.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaPublisherMetricsFactory {

    public static final DefaultKafkaPublisherMetricsFactory INSTANCE = new DefaultKafkaPublisherMetricsFactory();

    public DefaultKafkaPublisherMetrics create(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
        return new DefaultKafkaPublisherMetrics(context);
    }

    public static class DefaultKafkaPublisherMetrics {

        public record RecordKey(String topic,
                                @Nullable Integer partition,
                                @Nullable Class<? extends Throwable> errorType,
                                @Nullable Tags extraTags) {

            public RecordKey withExtraTags(Tags tags) {
                return new RecordKey(topic, partition, errorType, tags);
            }
        }

        protected final Map<RecordKey, Timer> recordDurationCache = new ConcurrentHashMap<>();
        protected final Map<RecordKey, Counter> sentMessagesCache = new ConcurrentHashMap<>();

        protected final DefaultKafkaPublisherTelemetry.TelemetryContext context;

        public DefaultKafkaPublisherMetrics(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void reportHandleRecordTook(String topic,
                                           @Nullable Object recordKey,
                                           Object recordValue,
                                           @Nullable ProducerRecord<byte[], byte[]> record, // when serialization failed
                                           @Nullable RecordMetadata metadata,
                                           @Nullable Throwable error,
                                           long startedRecordHandleInNanos) {
            var took = System.nanoTime() - startedRecordHandleInNanos;

            var key = createMetricRecordKey(topic, recordKey, recordValue, record, metadata, error);
            var durationMeter = this.recordDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordDuration(key, topic, recordKey, recordValue, record, metadata, error);
                return builder.register(context.meterRegistry());
            });
            durationMeter.record(took, TimeUnit.NANOSECONDS);

            var counterMeter = this.sentMessagesCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordSentCounter(key, topic, recordKey, recordValue, record, metadata, error);
                return builder.register(context.meterRegistry());
            });
            counterMeter.increment();
        }

        protected RecordKey createMetricRecordKey(String topic,
                                                  @Nullable Object key,
                                                  Object value,
                                                  @Nullable ProducerRecord<byte[], byte[]> record,
                                                  @Nullable RecordMetadata metadata,
                                                  @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            var partition = metadata == null ? null : metadata.partition();
            return new RecordKey(topic, partition, errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricRecordDuration(RecordKey metricKey,
                                                           String topic,
                                                           @Nullable Object key,
                                                           Object value,
                                                           @Nullable ProducerRecord<byte[], byte[]> record, // when serialization failed
                                                           @Nullable RecordMetadata metadata,
                                                           @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var partition = metricKey.partition == null ? "" : String.valueOf(metricKey.partition);

            var tags = new ArrayList<Tag>(9 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_CONFIG_PATH, context.publisherConfig()));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_NAME_SIMPLE, context.publisherSimpleName()));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_NAME_CANONICAL, context.publisherCanonicalName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), metricKey.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partition));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("messaging.client.operation.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricRecordSentCounter(RecordKey metricKey,
                                                                String topic,
                                                                @Nullable Object key,
                                                                Object value,
                                                                @Nullable ProducerRecord<byte[], byte[]> record, // when serialization failed
                                                                @Nullable RecordMetadata metadata,
                                                                @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var partition = metricKey.partition == null ? "" : String.valueOf(metricKey.partition);

            var tags = new ArrayList<Tag>(9 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_CONFIG_PATH, context.publisherConfig()));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_NAME_SIMPLE, context.publisherSimpleName()));
            tags.add(Tag.of(DefaultKafkaPublisherTelemetry.SYSTEM_NAME_CANONICAL, context.publisherCanonicalName()));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), metricKey.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partition));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Counter.builder("messaging.client.sent.messages")
                .tags(Tags.of(tags));
        }
    }
}
