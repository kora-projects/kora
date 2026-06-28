package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.telemetry.FallbackObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public class DefaultFallbackObservation implements FallbackObservation {

    protected final DefaultFallbackTelemetry.TelemetryContext context;
    protected final DefaultFallbackLoggerFactory.DefaultFallbackLogger logger;
    protected final DefaultFallbackMetricsFactory.DefaultFallbackMetrics metrics;
    protected final long startNanos = System.nanoTime();

    @Nullable
    protected Throwable throwable;
    @Nullable
    protected Throwable exception;

    public DefaultFallbackObservation(DefaultFallbackTelemetry.TelemetryContext context,
                                      DefaultFallbackLoggerFactory.DefaultFallbackLogger logger,
                                      DefaultFallbackMetricsFactory.DefaultFallbackMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartFallback();
    }

    @Override
    public void recordExecute(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (this.throwable != null) {
            this.metrics.recordExecute(this.throwable);
            this.logger.logExecute(System.nanoTime() - this.startNanos, this.throwable, this.exception);
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }
}
