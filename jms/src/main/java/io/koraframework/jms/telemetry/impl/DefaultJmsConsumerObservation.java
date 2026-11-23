package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultJmsConsumerObservation implements JmsConsumerObservation {

    private static final Logger log = LoggerFactory.getLogger(DefaultJmsConsumerObservation.class);
    private final AtomicBoolean ended = new AtomicBoolean();

    protected final long startNanos = System.nanoTime();
    protected final DefaultJmsConsumerTelemetry.TelemetryContext context;
    protected final DefaultJmsConsumerLoggerFactory.DefaultJmsConsumerLogger logger;
    protected final DefaultJmsConsumerMetricsFactory.DefaultJmsConsumerMetrics metrics;
    protected final Message message;
    protected final String destination;
    protected final Span span;

    @Nullable
    protected Throwable error;

    public DefaultJmsConsumerObservation(
        DefaultJmsConsumerTelemetry.TelemetryContext context,
        DefaultJmsConsumerLoggerFactory.DefaultJmsConsumerLogger logger,
        DefaultJmsConsumerMetricsFactory.DefaultJmsConsumerMetrics metrics,
        Message message,
        String destination,
        Span span
    ) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.message = message;
        this.destination = destination;
        this.span = span;
        safely(() -> this.logger.logStart(message, destination));
    }

    @Override
    public void observeProcess() throws JMSException {
        this.logger.logProcess(this.message, this.destination);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        var processingTime = System.nanoTime() - this.startNanos;
        try {
            safely(() -> this.metrics.recordEnd(this.destination, this.error, processingTime));
            safely(() -> this.logger.logEnd(this.message, this.destination, processingTime, this.error));
        } finally {
            this.span.end();
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("JMS observation callback failed for '{}'", context.queueName(), e);
        }
    }
}
