package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryObservation.StopReason;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultRetryObservation implements RetryObservation {

    protected final DefaultRetryTelemetry.TelemetryContext context;
    protected final DefaultRetryLoggerFactory.DefaultRetryLogger logger;
    protected final DefaultRetryMetricsFactory.DefaultRetryMetrics metrics;
    protected final long startNanos = System.nanoTime();

    protected int attempts;
    @Nullable
    protected StopReason stopReason;
    protected long lastDelayInNanos;
    protected final List<Long> attemptDelaysInNanos = new ArrayList<>();
    @Nullable
    protected Throwable exception;

    public DefaultRetryObservation(DefaultRetryTelemetry.TelemetryContext context,
                                   DefaultRetryLoggerFactory.DefaultRetryLogger logger,
                                   DefaultRetryMetricsFactory.DefaultRetryMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartRetry();
    }

    @Override
    public void recordAttempt(long delayInNanos) {
        this.attempts++;
        this.lastDelayInNanos = delayInNanos;
        this.attemptDelaysInNanos.add(delayInNanos);
    }

    @Override
    public void recordExhausted(StopReason reason, int totalAttempts) {
        this.attempts = totalAttempts;
        this.stopReason = reason;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        for (var attemptDelayInNanos : this.attemptDelaysInNanos) {
            this.metrics.recordAttempt(attemptDelayInNanos);
        }
        if (this.stopReason != null) {
            this.metrics.recordExhausted(this.attempts, this.stopReason);
        }
        this.logger.logRetry(this.attempts, this.stopReason, this.lastDelayInNanos, System.nanoTime() - this.startNanos, this.exception);
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }
}
