package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTransactionObservation;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetry.TelemetryContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class DefaultKafkaPublisherTransactionObservation implements KafkaPublisherTransactionObservation {

    protected final TelemetryContext context;
    protected final DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger logger;
    protected final Span span;

    @Nullable
    private Throwable error;

    public DefaultKafkaPublisherTransactionObservation(TelemetryContext context,
                                                       DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger logger,
                                                       Span span) {
        this.context = context;
        this.logger = logger;
        this.span = span;
    }

    @Override
    public void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
        this.logger.logTxOffsets(offsets);
    }

    @Override
    public void observeCommit() {
        this.logger.logTxCommitStart();
    }

    @Override
    public void observeRollback(@Nullable Throwable e) {
        this.span.setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "rollback");
        this.span.setStatus(StatusCode.ERROR);
        if (e == null) {
            this.logger.logTxRollbackStart(null);
        } else {
            this.span.recordException(e);
            this.logger.logTxRollbackStart(e);
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
        }
        this.logger.logTxEnd(error);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
