package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.*;
import io.koraframework.resilient.circuitbreaker.telemetry.*;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public class DefaultCircuitBreakerObservation implements CircuitBreakerObservation {

    protected final DefaultCircuitBreakerTelemetry.TelemetryContext context;
    protected final DefaultCircuitBreakerLoggerFactory.DefaultCircuitBreakerLogger logger;
    protected final DefaultCircuitBreakerMetricsFactory.DefaultCircuitBreakerMetrics metrics;
    protected final long startNanos = System.nanoTime();

    protected CircuitBreaker.@Nullable State state;
    protected CircuitBreakerObservation.@Nullable CallAcquireStatus callStatus;
    protected CircuitBreaker.@Nullable State newState;
    @Nullable
    protected Throwable exception;

    public DefaultCircuitBreakerObservation(DefaultCircuitBreakerTelemetry.TelemetryContext context,
                                            DefaultCircuitBreakerLoggerFactory.DefaultCircuitBreakerLogger logger,
                                            DefaultCircuitBreakerMetricsFactory.DefaultCircuitBreakerMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        logger.logStartAcquire();
    }

    @Override
    public void recordCallAcquire(CircuitBreaker.State state, CircuitBreakerObservation.CallAcquireStatus callStatus) {
        this.state = state;
        this.callStatus = callStatus;
    }

    @Override
    public void recordStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState) {
        this.newState = newState;
        this.logger.logStateChange(previousState, newState);
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (this.newState != null) {
            this.metrics.recordState(this.newState);
        }
        if (this.state != null && this.callStatus != null) {
            this.metrics.recordCallAcquire(this.state, this.callStatus);
            this.logger.logAcquire(this.state, this.callStatus, System.nanoTime() - this.startNanos, this.exception);
        }
    }
}
