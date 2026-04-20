package io.koraframework.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultKafkaConsumerMetricsFactory {

    public static final DefaultKafkaConsumerMetricsFactory INSTANCE = new DefaultKafkaConsumerMetricsFactory();

    public DefaultKafkaConsumerMetrics create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        return new DefaultKafkaConsumerMetrics(context);
    }

    public static class DefaultKafkaConsumerMetrics {

        public record RecordsKey(@Nullable String errorType) {}

        public record RecordKey(String topic, int partition, @Nullable String errorType) {}

        public record LagKey(String topic, int partition) {}

        protected final Map<RecordsKey, Timer> batchDurationCache = new ConcurrentHashMap<>();
        protected final Map<RecordKey, Timer> recordDurationCache = new ConcurrentHashMap<>();
        protected final Map<LagKey, AtomicLong> lagGaugeCache = new ConcurrentHashMap<>();

        protected final DefaultKafkaConsumerTelemetry.TelemetryContext context;

        public DefaultKafkaConsumerMetrics(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void reportHandleRecordsBatchTook(ConsumerRecords<?, ?> records,
                                                 long startedRecordsHandleInNanos,
                                                 @Nullable Throwable error) {
            var took = System.nanoTime() - startedRecordsHandleInNanos;

            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var key = new RecordsKey(errorValue);
            var meter = this.batchDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordsDuration(records, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportHandleRecordTook(ConsumerRecord<?, ?> record,
                                           long startedRecordHandleInNanos,
                                           @Nullable Throwable error) {
            var took = System.nanoTime() - startedRecordHandleInNanos;

            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var key = new RecordKey(record.topic(), record.partition(), errorValue);
            var meter = this.recordDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordDuration(record, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportTopicLag(TopicPartition partition, long lag) {
            var key = new LagKey(partition.topic(), partition.partition());
            var lagCounter = this.lagGaugeCache.computeIfAbsent(key, _ -> {
                var counter = new AtomicLong();
                var builder = createMetricLag(partition, counter);
                builder.register(context.meterRegistry());
                return counter;
            });
            lagCounter.set(lag);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Timer.Builder createMetricRecordsDuration(ConsumerRecords<?, ?> records,
                                                            @Nullable Throwable error) {
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var tags = new ArrayList<Tag>(5 + context.config().metrics().tags().size());
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME, context.listenerName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));

            return Timer.builder("messaging.process.batch.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(tags);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Timer.Builder createMetricRecordDuration(ConsumerRecord<?, ?> record,
                                                           @Nullable Throwable error) {
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var tags = new ArrayList<Tag>(7 + context.config().metrics().tags().size());
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME, context.listenerName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), record.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(record.partition())));

            return Timer.builder("messaging.process.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(tags);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Gauge.Builder createMetricLag(TopicPartition partition, AtomicLong counter) {
            var tags = new ArrayList<Tag>(6 + context.config().metrics().tags().size());
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME, context.listenerName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(partition.partition())));

            return Gauge.builder("messaging.kafka.consumer.lag", counter, AtomicLong::get)
                .tags(tags);
        }
    }
}
