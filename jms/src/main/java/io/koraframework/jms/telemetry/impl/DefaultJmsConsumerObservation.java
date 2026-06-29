package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

import javax.jms.JMSException;
import javax.jms.Message;

public class DefaultJmsConsumerObservation implements JmsConsumerObservation {

    protected final long startNanos = System.nanoTime();
    protected final DefaultJmsConsumerTelemetry.TelemetryContext context;
    protected final DefaultJmsConsumerLoggerFactory.DefaultJmsConsumerLogger logger;
    protected final DefaultJmsConsumerMetricsFactory.DefaultJmsConsumerMetrics metrics;
    protected final Message message;
    protected final String destination;
    protected final Span span;

    @Nullable
    protected Throwable error;

    public DefaultJmsConsumerObservation(DefaultJmsConsumerTelemetry.TelemetryContext context,
                                         DefaultJmsConsumerLoggerFactory.DefaultJmsConsumerLogger logger,
                                         DefaultJmsConsumerMetricsFactory.DefaultJmsConsumerMetrics metrics,
                                         Message message,
                                         String destination,
                                         Span span) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.message = message;
        this.destination = destination;
        this.span = span;
        this.logger.logStart(message, destination);
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
        var processingTime = System.nanoTime() - this.startNanos;
        this.metrics.recordEnd(this.destination, this.error, processingTime);
        this.logger.logEnd(this.message, this.destination, processingTime, this.error);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
