package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DefaultKafkaPublisherTransactionObservation implements KafkaPublisherTelemetry.KafkaPublisherTransactionObservation {

    private final Span span;
    private final Logger logger;
    private Throwable error;

    public DefaultKafkaPublisherTransactionObservation(Span span, Logger logger) {
        this.span = span;
        this.logger = logger;
    }

    @Override
    public void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
        if (logger.isTraceEnabled()) {
            var traceInfo = new HashMap<String, Map<Integer, Set<Long>>>();
            for (var metadataEntry : offsets.entrySet()) {
                var partitionInfo = traceInfo.computeIfAbsent(metadataEntry.getKey().topic(), k -> new HashMap<>());
                var offsetInfo = partitionInfo.computeIfAbsent(metadataEntry.getKey().partition(), k -> new TreeSet<>());
                offsetInfo.add(metadataEntry.getValue().offset());
            }
            var transactionMeta = traceInfo.entrySet().stream()
                .map(ti -> "topic=" + ti.getKey() + ", partitions=" + ti.getValue().entrySet().stream()
                    .map(pi -> "partition=" + pi.getKey() + ", offsets=" + pi.getValue().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ", "[", "]")))
                    .collect(Collectors.joining("], [", "[", "]")))
                .collect(Collectors.joining("], [", "[", "]"));

            logger.trace("Kafka Producer success sending '{}' transaction records with meta: {}", offsets.size(), transactionMeta);
        } else {
            logger.debug("Kafka Producer success sending '{}' transaction records", offsets.size());
        }
    }

    @Override
    public void observeCommit() {
        logger.debug("Kafka Producer committing transaction...");
    }

    @Override
    public void observeRollback(@Nullable Throwable e) {
        logger.debug("Kafka Producer rollback transaction...");
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
