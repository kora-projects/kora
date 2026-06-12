package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultKafkaConsumerLoggerFactory {

    public static final DefaultKafkaConsumerLoggerFactory INSTANCE = new DefaultKafkaConsumerLoggerFactory();

    public DefaultKafkaConsumerLogger create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.listenerCanonicalName());
        return new DefaultKafkaConsumerLogger(logger, context);
    }

    public static class DefaultKafkaConsumerLogger {

        protected final Logger logger;
        protected final DefaultKafkaConsumerTelemetry.TelemetryContext context;

        public DefaultKafkaConsumerLogger(Logger logger, DefaultKafkaConsumerTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logPollStart() {
            if (this.logger.isTraceEnabled()) {
                this.logger.atTrace()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .log("KafkaListener starting polling...");
            }
        }

        public void logRecordsReceived(ConsumerRecords<?, ?> records) {
            if (this.logger.isTraceEnabled()) {
                if (records.isEmpty()) {
                    this.logger.atTrace()
                        .addKeyValue("listenerConfig", context.listenerConfig())
                        .log("{} polled '0' records", context.listenerConfig());
                } else {
                    Map<String, List<Integer>> topicPartitionMap = new HashMap<>();
                    for (TopicPartition partition : records.partitions()) {
                        topicPartitionMap.computeIfAbsent(partition.topic(), _ -> new ArrayList<>())
                            .add(partition.partition());
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
                        .addKeyValue("listenerConfig", context.listenerConfig())
                        .addKeyValue("topics", arg)
                        .log("KafkaListener polled '{}' records, starting handling records...", records.count());
                }
            } else if (this.logger.isDebugEnabled() && !records.isEmpty()) {
                this.logger.atDebug()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .log("KafkaListener polled '{}' records, starting handling records...", records.count());
            }
        }

        public void logPollEnd(@Nullable ConsumerRecords<?, ?> records, @Nullable Throwable error) {
            var recordsCount = records == null ? 0 : records.count();
            if (error == null) {
                if (this.logger.isInfoEnabled()) {
                    this.logger.atInfo()
                        .addKeyValue("listenerConfig", context.listenerConfig())
                        .log("KafkaListener success '{}' records handled", recordsCount);
                }
            } else if (this.logger.isWarnEnabled()) {
                this.logger.atWarn()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .log("KafkaListener failed '{}' records handling due to: {}", recordsCount, error.getMessage());
            }
        }

        public void logRecordStart(ConsumerRecord<?, ?> record) {
            if (this.logger.isDebugEnabled()) {
                this.logger.atDebug()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("partition", record.partition())
                    .log("KafkaListener starting handling record...");
            }
        }

        public void logRecordEnd(ConsumerRecord<?, ?> record, @Nullable Throwable error) {
            if (error == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.atDebug()
                        .addKeyValue("listenerConfig", context.listenerConfig())
                        .addKeyValue("topic", record.topic())
                        .addKeyValue("offset", record.offset())
                        .addKeyValue("partition", record.partition())
                        .log("KafkaListener success record handled");
                }
            } else if (this.logger.isWarnEnabled()) {
                this.logger.atWarn()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("partition", record.partition())
                    .log("KafkaListener failed record handled due to: {}", error.getMessage());
            }
        }
    }
}
