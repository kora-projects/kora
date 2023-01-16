package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.KafkaConsumerMetricsConfig;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class MicrometerKafkaConsumerMetrics implements KafkaConsumerMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();
    private final KafkaConsumerMetricsConfig config;

    public MicrometerKafkaConsumerMetrics(MeterRegistry meterRegistry, @Nullable KafkaConsumerMetricsConfig config) {
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
        var builder = DistributionSummary.builder("messaging.consumer.duration");
        if (this.config != null && this.config.slo() != null) {
            builder.serviceLevelObjectives(this.config.slo().stream().mapToDouble(Double::doubleValue).toArray());
        } else {
            builder.serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000);
        }
        return builder.baseUnit("milliseconds")
            .tag("messaging.system", "kafka")
            .tag("messaging.destination", topicPartition.topic())
            .tag("messaging.destination_kind", "topic")
            .register(this.meterRegistry);
    }

    @Override
    public void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, Throwable ex) {
        double durationDouble = ((double) duration) / 1_000_000;
        this.metrics.get(new TopicPartition(record.topic(), record.partition())).record(durationDouble);
    }

    @Override
    public void onRecordsProcessed(ConsumerRecords<?, ?> records, long duration, Throwable ex) {
    }
}
