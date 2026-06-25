package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultKafkaConsumerMetricsFactory {

    public static final DefaultKafkaConsumerMetricsFactory INSTANCE = new DefaultKafkaConsumerMetricsFactory();

    public DefaultKafkaConsumerMetrics create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        return new DefaultKafkaConsumerMetrics(context);
    }

    public static class DefaultKafkaConsumerMetrics {

        public record RecordsKey(@Nullable Class<? extends Throwable> errorType,
                                 @Nullable Tags extraTags) {

            public RecordsKey withExtraTags(Tags tags) {
                return new RecordsKey(errorType, tags);
            }
        }

        public record RecordKey(String topic,
                                int partition,
                                @Nullable Class<? extends Throwable> errorType,
                                @Nullable Tags extraTags) {

            public RecordKey withExtraTags(Tags tags) {
                return new RecordKey(topic, partition, errorType, tags);
            }
        }

        public record LagKey(String topic,
                             int partition,
                             @Nullable Tags extraTags) {

            public LagKey withExtraTags(Tags tags) {
                return new LagKey(topic, partition, tags);
            }
        }

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

            var key = createMetricRecordsDurationKey(records, error);
            var meter = this.batchDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordsDuration(key, records, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportHandleRecordTook(ConsumerRecord<?, ?> record,
                                           long startedRecordHandleInNanos,
                                           @Nullable Throwable error) {
            var took = System.nanoTime() - startedRecordHandleInNanos;

            var key = createMetricRecordDurationKey(record, error);
            var meter = this.recordDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRecordDuration(key, record, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportTopicLag(TopicPartition partition, long lag) {
            var key = createMetricLagKey(partition);
            var lagCounter = this.lagGaugeCache.computeIfAbsent(key, _ -> {
                var counter = new AtomicLong();
                var builder = createMetricLag(key, partition, counter);
                builder.register(context.meterRegistry());
                return counter;
            });
            lagCounter.set(lag);
        }

        protected RecordsKey createMetricRecordsDurationKey(ConsumerRecords<?, ?> records,
                                                            @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new RecordsKey(errorType, null);
        }

        protected RecordKey createMetricRecordDurationKey(ConsumerRecord<?, ?> record,
                                                          @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new RecordKey(record.topic(), record.partition(), errorType, null);
        }

        protected LagKey createMetricLagKey(TopicPartition partition) {
            return new LagKey(partition.topic(), partition.partition(), null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricRecordsDuration(RecordsKey metricKey,
                                                            ConsumerRecords<?, ?> records,
                                                            @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();

            var tags = new ArrayList<Tag>(7 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_CONFIG_PATH, context.listenerConfig()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_SIMPLE, context.listenerSimpleName()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_CANONICAL, context.listenerCanonicalName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("messaging.process.batch.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricRecordDuration(RecordKey metricKey,
                                                           ConsumerRecord<?, ?> record,
                                                           @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();

            var tags = new ArrayList<Tag>(9 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_CONFIG_PATH, context.listenerConfig()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_SIMPLE, context.listenerSimpleName()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_CANONICAL, context.listenerCanonicalName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), metricKey.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(metricKey.partition())));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("messaging.process.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Gauge.Builder<AtomicLong> createMetricLag(LagKey metricKey, TopicPartition partition, AtomicLong counter) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var tags = new ArrayList<Tag>(8 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_CONFIG_PATH, context.listenerConfig()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_SIMPLE, context.listenerSimpleName()));
            tags.add(Tag.of(DefaultKafkaConsumerTelemetry.SYSTEM_NAME_CANONICAL, context.listenerCanonicalName()));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), metricKey.topic()));
            tags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(metricKey.partition())));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Gauge.builder("messaging.kafka.consumer.lag", counter, AtomicLong::get)
                .tags(Tags.of(tags));
        }
    }
}
