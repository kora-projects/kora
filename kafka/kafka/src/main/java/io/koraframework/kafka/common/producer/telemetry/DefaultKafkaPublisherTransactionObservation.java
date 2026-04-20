package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetry.TelemetryContext;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultKafkaPublisherTransactionObservation implements KafkaPublisherTelemetry.KafkaPublisherTransactionObservation {

    protected final TelemetryContext context;
    protected final Span span;

    @Nullable
    private Throwable error;

    public DefaultKafkaPublisherTransactionObservation(TelemetryContext context,
                                                       Span span) {
        this.context = context;
        this.span = span;
    }

    @Override
    public void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
        if (context.logger().isTraceEnabled()) {
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

            context.logger().atTrace()
                .addKeyValue("publisherName", context.publisherName())
                .addKeyValue("topics", arg)
                .log("KafkaPublisher success transaction records sent");
        } else {
            context.logger().atDebug()
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher success transaction records sent for '{}' topics and partitions",
                    offsets.size());
        }
    }

    @Override
    public void observeCommit() {
        this.context.logger()
            .atDebug()
            .addKeyValue("publisherName", context.publisherName())
            .log("KafkaPublisher starting transaction committing...");
    }

    @Override
    public void observeRollback(@Nullable Throwable e) {
        this.span.setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "rollback");
        this.span.setStatus(StatusCode.ERROR);
        if (e == null) {
            this.context.logger()
                .atDebug()
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher starting transaction rollback...");
        } else {
            var errorType = e.getClass().getCanonicalName();
            this.span.recordException(e);
            this.context.logger()
                .atWarn()
                .addKeyValue("errorType", errorType)
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher starting transaction rollback due to: {}", e.getMessage());
        }
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (error == null) {
            this.span.setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "commit");
            this.span.setStatus(StatusCode.OK);
            this.context.logger()
                .atDebug()
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher starting transaction rollback due to: {}", error.getMessage());
        } else {
            var errorType = error.getClass().getCanonicalName();
            this.context.logger()
                .atWarn()
                .addKeyValue("errorType", errorType)
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher failed transaction due to: {}", error.getMessage());
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
