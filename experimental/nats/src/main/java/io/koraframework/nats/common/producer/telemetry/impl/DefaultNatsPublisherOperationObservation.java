package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherLoggerFactory.DefaultNatsPublisherLogger;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherMetricsFactory.DefaultNatsPublisherMetrics;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetry.TelemetryContext;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultNatsPublisherOperationObservation implements NatsPublisherOperationObservation {
    protected final TelemetryContext context;
    protected final DefaultNatsPublisherLogger logger;
    protected final DefaultNatsPublisherMetrics metrics;
    protected final Span span;
    protected final String operation;
    protected final String subject;
    protected final long started = System.nanoTime();
    private final AtomicBoolean ended = new AtomicBoolean();
    private volatile @Nullable Throwable error;

    public DefaultNatsPublisherOperationObservation(TelemetryContext context, DefaultNatsPublisherLogger logger, DefaultNatsPublisherMetrics metrics,
                                                    Span span, String operation, String subject) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
        this.operation = operation;
        this.subject = subject;
        logger.start(operation, subject);
    }

    @Override
    public Span span() {
        return span;
    }

    @Override
    public void observeError(Throwable error) {
        this.error = error;
    }

    @Override
    public void observeResult(@Nullable Object result) {
        if (result instanceof PublishAck ack) {
            span.setAttribute("messaging.nats.stream", ack.getStream());
            span.setAttribute("messaging.nats.sequence", ack.getSeqno());
            span.setAttribute("messaging.nats.duplicate", ack.isDuplicate());
            if (ack.getBatchId() != null) {
                span.setAttribute("messaging.nats.batch.id", ack.getBatchId());
                span.setAttribute("messaging.batch.message_count", ack.getBatchSize());
            }
        } else if (result instanceof Message message && message.getData() != null) {
            span.setAttribute("messaging.message.body.size", message.getData().length);
        }
    }

    @Override
    public void end() {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        try {
            var failure = error;
            if (failure != null) {
                span.recordException(failure);
                span.setStatus(StatusCode.ERROR);
                span.setAttribute("error.type", failure.getClass().getName());
            } else {
                span.setStatus(StatusCode.OK);
            }
            metrics.duration(operation, subject, started, failure);
            logger.end(operation, subject, failure);
        } finally {
            span.end();
        }
    }
}
