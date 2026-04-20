package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaPublisherMetricsFactory {

    public static final DefaultKafkaPublisherMetricsFactory INSTANCE = new DefaultKafkaPublisherMetricsFactory();

    public DefaultKafkaPublisherMetrics create(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
        return new DefaultKafkaPublisherMetrics(context);
    }

    public static class DefaultKafkaPublisherMetrics {

        public record RecordKey(String topic, @Nullable Integer partition, @Nullable String errorType) {}

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

            var errorValue = error == null ? "" : error.getClass().getCanonicalName();
            var partition = metadata == null ? null : metadata.partition();

            var key = new RecordKey(topic, partition, errorValue);
            var durationMeter = this.recordDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordDuration(topic, recordKey, recordValue, record, metadata, error);
                return builder.register(context.meterRegistry());
            });
            durationMeter.record(took, TimeUnit.NANOSECONDS);

            var counterMeter = this.sentMessagesCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordSentCounter(topic, recordKey, recordValue, record, metadata, error);
                return builder.register(context.meterRegistry());
            });
            counterMeter.increment();
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Timer.Builder createMetricRecordDuration(String topic,
                                                           @Nullable Object key,
                                                           Object value,
                                                           @Nullable ProducerRecord<byte[], byte[]> record, // when serialization failed
                                                           @Nullable RecordMetadata metadata,
                                                           @Nullable Throwable error) {
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();
            var partition = metadata == null ? "" : String.valueOf(metadata.partition());

            var tags = new ArrayList<Tag>(7 + context.config().metrics().tags().size());
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME, context.publisherName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partition));

            return Timer.builder("messaging.client.operation.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(tags);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Counter.Builder createMetricRecordSentCounter(String topic,
                                                                @Nullable Object key,
                                                                Object value,
                                                                @Nullable ProducerRecord<byte[], byte[]> record, // when serialization failed
                                                                @Nullable RecordMetadata metadata,
                                                                @Nullable Throwable error) {
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();
            var partition = metadata == null ? "" : String.valueOf(metadata.partition());

            var tags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size());
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE.getKey(), MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME, context.publisherName()));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topic));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partition));

            return Counter.builder("messaging.client.sent.messages")
                .tags(tags);
        }
    }
}
