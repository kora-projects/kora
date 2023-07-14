package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DefaultKafkaProducerLogger implements KafkaProducerLogger {

    private static final Logger logger = LoggerFactory.getLogger(DefaultKafkaProducerLogger.class);

    @Override
    public void sendBegin(ProducerRecord<?, ?> record) {
        logger.debug("Kafka Producer sending record to topic {} and partition {}", record.topic(), record.partition());
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, Throwable e) {
        logger.warn("Kafka Producer error sending record to topic {} and partition {}", record.topic(), record.topic(), e);
    }

    @Override
    public void sendEnd(RecordMetadata metadata) {
        logger.debug("Kafka Producer success sending record to topic {} and partition {} and offset {}", metadata.topic(), metadata.partition(), metadata.offset());
    }

    @Override
    public void txBegin() {
        logger.debug("Kafka Producer starting transaction...");
    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
        if (logger.isTraceEnabled()) {
            final Map<String, Map<Integer, Set<Long>>> traceInfo = new HashMap<>();
            for (var metadataEntry : offsets.entrySet()) {
                final Map<Integer, Set<Long>> partitionInfo = traceInfo.computeIfAbsent(metadataEntry.getKey().topic(), k -> new HashMap<>());
                final Set<Long> offsetInfo = partitionInfo.computeIfAbsent(metadataEntry.getKey().partition(), k -> new TreeSet<>());
                offsetInfo.add(metadataEntry.getValue().offset());
            }

            final String transactionMeta = traceInfo.entrySet().stream()
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
    public void txCommit() {
        logger.debug("Kafka Producer committing transaction...");
    }

    @Override
    public void txRollback(@Nullable Throwable e) {
        logger.debug("Kafka Producer rollback transaction...");
    }
}
