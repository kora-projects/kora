package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.telemetry.TimeoutObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public class DefaultTimeoutObservation implements TimeoutObservation {

    protected final long timeToWaitInNanos;
    protected final DefaultTimeoutTelemetry.TelemetryContext context;
    protected final DefaultTimeoutLoggerFactory.DefaultTimeoutLogger logger;
    protected final DefaultTimeoutMetricsFactory.DefaultTimeoutMetrics metrics;
    protected final long startNanos = System.nanoTime();

    @Nullable
    protected Long timeoutInNanos;
    @Nullable
    protected Throwable exception;

    public DefaultTimeoutObservation(long timeToWaitInNanos,
                                     DefaultTimeoutTelemetry.TelemetryContext context,
                                     DefaultTimeoutLoggerFactory.DefaultTimeoutLogger logger,
                                     DefaultTimeoutMetricsFactory.DefaultTimeoutMetrics metrics) {
        this.timeToWaitInNanos = timeToWaitInNanos;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartWaiting(timeToWaitInNanos);
    }

    @Override
    public void recordTimeout(long timeoutInNanos) {
        this.timeoutInNanos = timeoutInNanos;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (this.timeoutInNanos != null) {
            this.metrics.recordTimeout(this.timeoutInNanos);
            this.logger.logTimeout(this.timeoutInNanos, System.nanoTime() - this.startNanos, this.exception);
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }
}
