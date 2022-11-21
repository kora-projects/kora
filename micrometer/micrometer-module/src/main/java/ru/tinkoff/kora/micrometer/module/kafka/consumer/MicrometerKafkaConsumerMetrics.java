package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerMetrics;

import java.util.concurrent.ConcurrentHashMap;

public class MicrometerKafkaConsumerMetrics implements KafkaConsumerMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();

    public MicrometerKafkaConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onRecordsReceived(ConsumerRecords<?, ?> records) {
        for (var partition : records.partitions()) {
            this.metrics.computeIfAbsent(partition, this::metrics);
        }
    }

    private DistributionSummary metrics(TopicPartition topicPartition) {
        // wait for opentelemetry standard https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/metrics/semantic_conventions
        return DistributionSummary.builder("messaging.consumer.duration")
            .serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000)
            .baseUnit("milliseconds")
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
