package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.SemanticAttributes;
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

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class Opentelemetry120KafkaConsumerMetrics implements KafkaConsumerMetrics, Lifecycle {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TopicPartition, LagGauge> lagMetrics = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;
    private final Properties driverProperties;

    public Opentelemetry120KafkaConsumerMetrics(MeterRegistry meterRegistry, Properties driverProperties, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.driverProperties = driverProperties;
    }

    @Override
    public void onRecordsReceived(ConsumerRecords<?, ?> records) {
        for (var partition : records.partitions()) {
            this.metrics.computeIfAbsent(partition, this::metrics);
        }
    }

    private DistributionSummary metrics(TopicPartition topicPartition) {
        var builder = DistributionSummary.builder("messaging.receive.duration")
            .serviceLevelObjectives(this.config.slo(null))
            .baseUnit("milliseconds")
            .tag(SemanticAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
            .tag(SemanticAttributes.MESSAGING_DESTINATION_NAME.getKey(), topicPartition.topic())
            ;
        var clientId = driverProperties.get(ProducerConfig.CLIENT_ID_CONFIG);
        if (clientId != null) {
            builder.tag(SemanticAttributes.MESSAGING_CLIENT_ID.getKey(), clientId.toString());
        }
        var groupId = driverProperties.get(ConsumerConfig.GROUP_ID_CONFIG);
        if (groupId != null) {
            builder.tag(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP.getKey(), groupId.toString());
        }
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
                .tag(SemanticAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
                .tag(SemanticAttributes.MESSAGING_DESTINATION_NAME.getKey(), partition.topic())
                .tag(SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), Objects.toString(partition.partition()))
                .register(meterRegistry);
        }
    }
}
