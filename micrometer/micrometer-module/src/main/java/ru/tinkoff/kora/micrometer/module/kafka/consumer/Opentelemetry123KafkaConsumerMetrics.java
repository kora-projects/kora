package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public final class Opentelemetry123KafkaConsumerMetrics implements KafkaConsumerMetrics, Lifecycle {

    private static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_NAME = stringKey("messaging.kafka.consumer.name");

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<DurationKey, DistributionSummary> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationBatchKey, DistributionSummary> metricsBatch = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TopicPartition, LagGauge> lagMetrics = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;
    private final Properties driverProperties;

    public Opentelemetry123KafkaConsumerMetrics(MeterRegistry meterRegistry, Properties driverProperties, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.driverProperties = driverProperties;
    }

    private record DurationKey(String topic, int partition, @Nullable Class<? extends Throwable> errorType) {}

    private record DurationBatchKey(String consumerName, @Nullable Class<? extends Throwable> errorType) {}

    private DistributionSummary metrics(DurationKey key) {
        var clientId = driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG);
        var groupId = driverProperties.get(ConsumerConfig.GROUP_ID_CONFIG);

        @SuppressWarnings("deprecation")
        var builder = DistributionSummary.builder("messaging.receive.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), key.topic())
            .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), String.valueOf(key.partition()))
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(key.partition()))
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString())
            .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP.getKey(), Objects.requireNonNullElse(groupId, "").toString());

        if (key.errorType() != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.meterRegistry);
    }

    private DistributionSummary metricBatch(DurationBatchKey key) {
        var clientId = driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG);
        var groupId = driverProperties.get(ConsumerConfig.GROUP_ID_CONFIG);

        var builder = DistributionSummary.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
            .tag(MESSAGING_KAFKA_CONSUMER_NAME.getKey(), key.consumerName())
            .tag(MessagingIncubatingAttributes.MESSAGING_OPERATION.getKey(), key.consumerName())
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString())
            .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP.getKey(), Objects.requireNonNullElse(groupId, "").toString());

        if (key.errorType() != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.meterRegistry);
    }

    @Override
    public void onRecordsReceived(ConsumerRecords<?, ?> records) {

    }

    @Override
    public void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000_000;
        var key = new DurationKey(record.topic(), record.partition(), ex != null ? ex.getClass() : null);

        this.metrics.computeIfAbsent(key, this::metrics).record(durationDouble);
    }

    @Override
    public void onRecordsProcessed(String consumerName, ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000_000;
        var key = new DurationBatchKey(consumerName, ex != null ? ex.getClass() : null);

        this.metricsBatch.computeIfAbsent(key, this::metricBatch).record(durationDouble);
    }

    @Override
    public void reportLag(String consumerName, TopicPartition partition, long lag) {
        lagMetrics.computeIfAbsent(partition, p -> new LagGauge(consumerName, p, meterRegistry)).offsetLag = lag;
    }

    @Override
    public void init() {

    }

    @Override
    public KafkaConsumerMetricsContext get(Consumer<?, ?> consumer) {
        // todo config?
        var micrometerMetrics = new KafkaClientMetrics(consumer);
        micrometerMetrics.bindTo(meterRegistry);

        return micrometerMetrics::close;
    }

    @Override
    public void release() {
        var metrics = new ArrayList<>(this.metrics.values());
        this.metrics.clear();
        for (var metric : metrics) {
            metric.close();
        }
        var lagMetrics = new ArrayList<>(this.lagMetrics.values());
        this.lagMetrics.clear();
        for (var lagMetric : lagMetrics) {
            lagMetric.gauge.close();
        }
    }

    private static class LagGauge {
        private final Gauge gauge;
        private volatile long offsetLag;

        @SuppressWarnings("deprecation")
        private LagGauge(String consumerName, TopicPartition partition, MeterRegistry meterRegistry) {
            gauge = Gauge.builder("messaging.kafka.consumer.lag", () -> offsetLag)
                .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
                .tag(MESSAGING_KAFKA_CONSUMER_NAME.getKey(), consumerName)
                .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic())
                .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), Objects.toString(partition.partition()))
                .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), Objects.toString(partition.partition()))
                .register(meterRegistry);
        }
    }
}
