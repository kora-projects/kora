package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;

public final class NoopCircuitBreakerMetricsFactory extends DefaultCircuitBreakerMetricsFactory {

    public static final NoopCircuitBreakerMetricsFactory INSTANCE = new NoopCircuitBreakerMetricsFactory();

    private NoopCircuitBreakerMetricsFactory() {}

    @Override
    public DefaultCircuitBreakerMetrics create(DefaultCircuitBreakerTelemetry.TelemetryContext context) {
        return NoopCircuitBreakerMetrics.INSTANCE;
    }

    public static final class NoopCircuitBreakerMetrics extends DefaultCircuitBreakerMetrics {

        public static final NoopCircuitBreakerMetrics INSTANCE = new NoopCircuitBreakerMetrics();

        private NoopCircuitBreakerMetrics() {
            super(DefaultCircuitBreakerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordCallAcquire(CircuitBreaker.State state, CircuitBreakerObservation.CallAcquireStatus callStatus) {}

        @Override
        public void recordState(CircuitBreaker.State newState) {}
    }
}
