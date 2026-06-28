package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public class DefaultRateLimiterObservation implements RateLimiterObservation {

    protected final DefaultRateLimiterTelemetry.TelemetryContext context;
    protected final DefaultRateLimiterLoggerFactory.DefaultRateLimiterLogger logger;
    protected final DefaultRateLimiterMetricsFactory.DefaultRateLimiterMetrics metrics;
    protected final long startNanos = System.nanoTime();

    @Nullable
    protected Boolean acquired;
    @Nullable
    protected Throwable exception;

    public DefaultRateLimiterObservation(DefaultRateLimiterTelemetry.TelemetryContext context,
                                         DefaultRateLimiterLoggerFactory.DefaultRateLimiterLogger logger,
                                         DefaultRateLimiterMetricsFactory.DefaultRateLimiterMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartAcquire();
    }

    @Override
    public void recordAcquire(boolean acquired) {
        this.acquired = acquired;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (this.acquired != null) {
            this.metrics.recordAcquire(this.acquired);
            this.logger.logAcquire(this.acquired, System.nanoTime() - this.startNanos, this.exception);
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }
}
