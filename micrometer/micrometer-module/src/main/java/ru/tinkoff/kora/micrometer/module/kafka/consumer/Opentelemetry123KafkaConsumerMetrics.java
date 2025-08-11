package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.RecordsDurationKey;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.RecordDurationKey;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.TopicLagKey;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.MicrometerKafkaConsumerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public final class Opentelemetry123KafkaConsumerMetrics implements KafkaConsumerMetrics, Lifecycle {

    private final ConcurrentHashMap<RecordDurationKey, DistributionSummary> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RecordsDurationKey, DistributionSummary> metricsBatch = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TopicLagKey, LagGauge> lagMetrics = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final Properties driverProperties;
    private final TelemetryConfig.MetricsConfig config;
    private final MicrometerKafkaConsumerTagsProvider tagsProvider;
    @Nullable
    private final String clientId;
    @Nullable
    private final String groupId;

    public Opentelemetry123KafkaConsumerMetrics(MeterRegistry meterRegistry,
                                                Properties driverProperties,
                                                TelemetryConfig.MetricsConfig config,
                                                MicrometerKafkaConsumerTagsProvider tagsProvider) {
        this.tagsProvider = tagsProvider;
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.driverProperties = driverProperties;

        var clientIdObj = driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG);
        var groupIdObj = driverProperties.get(ConsumerConfig.GROUP_ID_CONFIG);
        this.clientId = (clientIdObj instanceof String s) ? s : null;
        this.groupId = (groupIdObj instanceof String s) ? s : null;
    }

    private DistributionSummary metrics(RecordDurationKey key) {
        var builder = DistributionSummary.builder("messaging.receive.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tags(tagsProvider.getRecordDurationTags(clientId, groupId, driverProperties, key));

        return builder.register(this.meterRegistry);
    }

    private DistributionSummary metricBatch(RecordsDurationKey key) {
        var builder = DistributionSummary.builder("messaging.process.batch.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tags(tagsProvider.getRecordsDurationTags(clientId, groupId, driverProperties, key));

        return builder.register(this.meterRegistry);
    }

    @Override
    public void onRecordsReceived(ConsumerRecords<?, ?> records) {

    }

    @Override
    public void onRecordProcessed(String consumerName, ConsumerRecord<?, ?> record, long duration, @Nullable Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000_000;
        var key = new RecordDurationKey(consumerName, record.topic(), record.partition(), ex != null ? ex.getClass() : null);

        this.metrics.computeIfAbsent(key, this::metrics).record(durationDouble);
    }

    @Override
    public void onRecordsProcessed(String consumerName, ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000_000;
        var key = new RecordsDurationKey(consumerName, ex != null ? ex.getClass() : null);

        this.metricsBatch.computeIfAbsent(key, this::metricBatch).record(durationDouble);
    }

    @Override
    public void reportLag(String consumerName, TopicPartition partition, long lag) {
        var key = new TopicLagKey(consumerName, partition.topic(), partition.partition());
        lagMetrics.computeIfAbsent(key, k -> new LagGauge(k, clientId, driverProperties, tagsProvider, meterRegistry)).offsetLag = lag;
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

        private LagGauge(TopicLagKey key,
                         @Nullable String clientId,
                         Properties driverProperties,
                         MicrometerKafkaConsumerTagsProvider consumerTagsProvider,
                         MeterRegistry meterRegistry) {
            gauge = Gauge.builder("messaging.kafka.consumer.lag", () -> offsetLag)
                .tags(consumerTagsProvider.getTopicLagTags(clientId, driverProperties, key))
                .register(meterRegistry);
        }
    }
}
