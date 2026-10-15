package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public class DefaultBulkheadObservation implements BulkheadObservation {

    protected final DefaultBulkheadTelemetry.TelemetryContext context;
    protected final DefaultBulkheadLoggerFactory.DefaultBulkheadLogger logger;
    protected final DefaultBulkheadMetricsFactory.DefaultBulkheadMetrics metrics;
    protected final long startNanos = System.nanoTime();
    protected long acquiredNanos;

    @Nullable protected Boolean acquired;
    @Nullable protected Throwable exception;

    public DefaultBulkheadObservation(
        DefaultBulkheadTelemetry.TelemetryContext context,
        DefaultBulkheadLoggerFactory.DefaultBulkheadLogger logger,
        DefaultBulkheadMetricsFactory.DefaultBulkheadMetrics metrics
    ) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartAcquire();
    }

    @Override
    public void recordAcquire(boolean acquired) {
        this.acquired = acquired;
        if (acquired) {
            this.acquiredNanos = System.nanoTime();
        }
        this.metrics.recordAcquire(acquired);
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (this.acquired != null) {
            if (this.acquired) {
                this.metrics.recordDuration(System.nanoTime() - this.acquiredNanos);
            }
            this.logger.logAcquire(this.acquired, System.nanoTime() - this.startNanos, this.exception);
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }

    @Override
    public void recordQueueWait(long durationNanos) {
        this.metrics.recordQueueWait(durationNanos);
    }
}
