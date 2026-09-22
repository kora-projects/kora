package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.utils.KafkaHeaderUtils;
import io.koraframework.kafka.common.utils.KafkaArgMaskingStrategy;
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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultKafkaConsumerLoggerFactory {

    public static final DefaultKafkaConsumerLoggerFactory INSTANCE = new DefaultKafkaConsumerLoggerFactory((key, value) -> "***", new DefaultKafkaConsumerBodyConverter());

    private final KafkaArgMaskingStrategy maskingStrategy;
    private final DefaultKafkaConsumerBodyConverter bodyConverter;

    public DefaultKafkaConsumerLoggerFactory(KafkaArgMaskingStrategy maskingStrategy, DefaultKafkaConsumerBodyConverter bodyConverter) {
        this.maskingStrategy = maskingStrategy;
        this.bodyConverter = bodyConverter;
    }

    public DefaultKafkaConsumerLogger create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.listenerCanonicalName());
        return new DefaultKafkaConsumerLogger(logger, this.maskingStrategy, this.bodyConverter, context);
    }

    public static class DefaultKafkaConsumerLogger {

        protected final Logger logger;
        protected final DefaultKafkaConsumerTelemetry.TelemetryContext context;
        protected final KafkaArgMaskingStrategy maskingStrategy;
        protected final Set<String> maskedHeaders;
        protected final DefaultKafkaConsumerBodyConverter bodyConverter;

        public DefaultKafkaConsumerLogger(Logger logger,
                                          KafkaArgMaskingStrategy maskingStrategy,
                                          DefaultKafkaConsumerBodyConverter bodyConverter,
                                          DefaultKafkaConsumerTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.maskingStrategy = maskingStrategy;
            this.bodyConverter = bodyConverter;
            this.maskedHeaders = context.config().logging().maskHeaders().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
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
                        .addKeyValue("recordsCount", 0)
                        .log("KafkaListener polled records");
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
                        .addKeyValue("recordsCount", records.count())
                        .log("KafkaListener polled records, starting handling records");
                }
            } else if (this.logger.isDebugEnabled() && !records.isEmpty()) {
                this.logger.atDebug()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("recordsCount", records.count())
                    .log("KafkaListener polled records, starting handling records");
            }
        }

        public void logPollEnd(@Nullable ConsumerRecords<?, ?> records, @Nullable Throwable error) {
            var recordsCount = records == null ? 0 : records.count();
            if (error == null) {
                if (this.logger.isInfoEnabled()) {
                    this.logger.atInfo()
                        .addKeyValue("listenerConfig", context.listenerConfig())
                        .addKeyValue("recordsCount", recordsCount)
                        .log("KafkaListener records handled");
                }
            } else if (this.logger.isWarnEnabled()) {
                var log = this.logger.atWarn()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("recordsCount", recordsCount)
                    .addKeyValue("exceptionType", error.getClass().getCanonicalName());
                if (error.getMessage() != null) {
                    log.addKeyValue("exceptionMessage", error.getMessage());
                }
                log.log("KafkaListener records handling failed");
            }
        }

        public void logRecordStart(ConsumerRecord<?, ?> record) {
            if (this.logger.isDebugEnabled()) {
                var log = this.logger.isTraceEnabled() ? this.logger.atTrace() : this.logger.atDebug();
                log
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("partition", record.partition());
                if (this.logger.isTraceEnabled() && record.headers().iterator().hasNext()) {
                    log.addKeyValue("headers", KafkaHeaderUtils.toMaskedString(this.maskedHeaders, this.maskingStrategy, record.headers()));
                }
                if (this.logger.isTraceEnabled()) {
                    var key = this.bodyConverter.convertKey(record);
                    if (key != null) {
                        log.addKeyValue("key", key);
                    }
                    var value = this.bodyConverter.convertValue(record);
                    if (value != null) {
                        log.addKeyValue("value", value);
                    }
                }
                log.log("KafkaListener starting handling record...");
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
                var log = this.logger.atWarn()
                    .addKeyValue("listenerConfig", context.listenerConfig())
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("partition", record.partition())
                    .addKeyValue("exceptionType", error.getClass().getCanonicalName());
                if (error.getMessage() != null) {
                    log.addKeyValue("exceptionMessage", error.getMessage());
                }
                log.log("KafkaListener record handling failed");
            }
        }
    }
}
