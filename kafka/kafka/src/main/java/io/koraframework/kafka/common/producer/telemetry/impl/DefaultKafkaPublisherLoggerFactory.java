package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultKafkaPublisherLoggerFactory {

    public static final DefaultKafkaPublisherLoggerFactory INSTANCE = new DefaultKafkaPublisherLoggerFactory();

    public DefaultKafkaPublisherLogger create(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.publisherCanonicalName());
        return new DefaultKafkaPublisherLogger(logger, context);
    }

    public static class DefaultKafkaPublisherLogger {

        protected final Logger logger;
        protected final DefaultKafkaPublisherTelemetry.TelemetryContext context;

        public DefaultKafkaPublisherLogger(Logger logger, DefaultKafkaPublisherTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logRecordStart(ProducerRecord<byte[], byte[]> record) {
            if (this.logger.isDebugEnabled()) {
                this.logger.atDebug()
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("publisherConfig", context.publisherConfig())
                    .log("KafkaPublisher starting record sending...");
            }
        }

        public void logRecordEnd(String topic, @Nullable RecordMetadata metadata, @Nullable Throwable error) {
            if (error != null) {
                if (this.logger.isWarnEnabled()) {
                    var errorType = error.getClass().getCanonicalName();
                    var log = this.logger.atWarn()
                        .addKeyValue("exceptionType", errorType)
                        .addKeyValue("topic", topic)
                        .addKeyValue("publisherConfig", context.publisherConfig());
                    if (error.getMessage() != null) {
                        log.addKeyValue("exceptionMessage", error.getMessage());
                    }
                    log.log("KafkaPublisher record sending failed");
                }
            } else if (metadata != null && this.logger.isInfoEnabled()) {
                this.logger.atInfo()
                    .addKeyValue("topic", metadata.topic())
                    .addKeyValue("partition", metadata.partition())
                    .addKeyValue("offset", metadata.offset())
                    .addKeyValue("publisherConfig", context.publisherConfig())
                    .log("KafkaPublisher success record sent");
            }
        }

        public void logTxOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {
            if (this.logger.isTraceEnabled()) {
                Map<String, List<Integer>> topicPartitionMap = new HashMap<>();
                for (var entry : offsets.entrySet()) {
                    topicPartitionMap.computeIfAbsent(entry.getKey().topic(), _ -> new ArrayList<>())
                        .add(entry.getKey().partition());
                }

                var arg = (StructuredArgumentWriter) gen -> {
                    gen.writeStartObject();
                    topicPartitionMap.forEach((topic, partitions) -> {
                        gen.writeName(topic);
                        gen.writeStartArray();
                        for (Integer partition : partitions) {
                            gen.writeNumber(partition);
                        }
                        gen.writeEndArray();
                    });
                    gen.writeEndObject();
                };

                this.logger.atTrace()
                    .addKeyValue("publisherConfig", context.publisherConfig())
                    .addKeyValue("topics", arg)
                    .addKeyValue("offsetsCount", offsets.size())
                    .log("KafkaPublisher success transaction records sent");
            } else if (this.logger.isDebugEnabled()) {
                this.logger.atDebug()
                    .addKeyValue("publisherConfig", context.publisherConfig())
                    .addKeyValue("offsetsCount", offsets.size())
                    .log("KafkaPublisher success transaction records sent");
            }
        }

        public void logTxCommitStart() {
            if (this.logger.isDebugEnabled()) {
                this.logger.atDebug()
                    .addKeyValue("publisherConfig", context.publisherConfig())
                    .log("KafkaPublisher starting transaction committing...");
            }
        }

        public void logTxRollbackStart(@Nullable Throwable error) {
            if (error == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.atDebug()
                        .addKeyValue("publisherConfig", context.publisherConfig())
                        .log("KafkaPublisher starting transaction rollback...");
                }
            } else if (this.logger.isWarnEnabled()) {
                var errorType = error.getClass().getCanonicalName();
                var log = this.logger.atWarn()
                    .addKeyValue("exceptionType", errorType)
                    .addKeyValue("publisherConfig", context.publisherConfig());
                if (error.getMessage() != null) {
                    log.addKeyValue("exceptionMessage", error.getMessage());
                }
                log.log("KafkaPublisher starting transaction rollback");
            }
        }

        public void logTxEnd(@Nullable Throwable error) {
            if (error == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.atDebug()
                        .addKeyValue("publisherConfig", context.publisherConfig())
                        .log("KafkaPublisher success transaction committed");
                }
            } else if (this.logger.isWarnEnabled()) {
                var errorType = error.getClass().getCanonicalName();
                var log = this.logger.atWarn()
                    .addKeyValue("exceptionType", errorType)
                    .addKeyValue("publisherConfig", context.publisherConfig());
                if (error.getMessage() != null) {
                    log.addKeyValue("exceptionMessage", error.getMessage());
                }
                log.log("KafkaPublisher transaction failed");
            }
        }
    }
}
