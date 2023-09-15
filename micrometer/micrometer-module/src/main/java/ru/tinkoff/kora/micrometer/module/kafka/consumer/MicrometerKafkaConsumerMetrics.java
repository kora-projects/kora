package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.KafkaConsumerMetricsConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class MicrometerKafkaConsumerMetrics implements KafkaConsumerMetrics, Lifecycle {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TopicPartition, LagGauge> lagMetrics = new ConcurrentHashMap<>();
    private final KafkaConsumerMetricsConfig config;

    public MicrometerKafkaConsumerMetrics(MeterRegistry meterRegistry, KafkaConsumerMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void onRecordsReceived(ConsumerRecords<?, ?> records) {
        for (var partition : records.partitions()) {
            this.metrics.computeIfAbsent(partition, this::metrics);
        }
    }

    private DistributionSummary metrics(TopicPartition topicPartition) {
        // wait for opentelemetry standard https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/metrics/semantic_conventions
        var builder = DistributionSummary.builder("messaging.consumer.duration")
            .serviceLevelObjectives(this.config.slo())
            .baseUnit("milliseconds")
            .tag("messaging.system", "kafka")
            .tag("messaging.destination", topicPartition.topic())
            .tag("messaging.destination_kind", "topic");
        return builder.register(this.meterRegistry);
    }

    @Override
    public void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000;
        this.metrics.get(new TopicPartition(record.topic(), record.partition())).record(durationDouble);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        lagMetrics.computeIfAbsent(partition, p -> new LagGauge(p, meterRegistry)).offsetLag = lag;
    }

    @Override
    public void onRecordsProcessed(ConsumerRecords<?, ?> records, long duration, Throwable ex) {
    }

    @Override
    public void init() {
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

        private LagGauge(TopicPartition partition, MeterRegistry meterRegistry) {
            gauge = Gauge.builder("messaging.kafka.consumer.lag", () -> offsetLag)
                .tag("messaging.system", "kafka")
                .tag("messaging.destination", partition.topic())
                .tag("messaging.destination_kind", "topic")
                .register(meterRegistry);
        }
    }
}
