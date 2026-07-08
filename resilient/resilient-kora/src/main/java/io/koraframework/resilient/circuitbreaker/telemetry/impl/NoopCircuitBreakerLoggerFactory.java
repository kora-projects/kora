package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopCircuitBreakerLoggerFactory extends DefaultCircuitBreakerLoggerFactory {

    public static final NoopCircuitBreakerLoggerFactory INSTANCE = new NoopCircuitBreakerLoggerFactory();

    private NoopCircuitBreakerLoggerFactory() {}

    @Override
    public DefaultCircuitBreakerLogger create(DefaultCircuitBreakerTelemetry.TelemetryContext context) {
        return NoopCircuitBreakerLogger.INSTANCE;
    }

    public static final class NoopCircuitBreakerLogger extends DefaultCircuitBreakerLogger {

        public static final NoopCircuitBreakerLogger INSTANCE = new NoopCircuitBreakerLogger();

        private NoopCircuitBreakerLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultCircuitBreakerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartAcquire() {}

        @Override
        public void logAcquire(CircuitBreaker.State state,
                               CircuitBreakerObservation.CallAcquireStatus callStatus,
                               long processingTimeNanos,
                               @Nullable Throwable exception) {}

        @Override
        public void logStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState) {}

        @Override
        public void logResult(CircuitBreaker.State state,
                              CircuitBreakerObservation.CallResult callResult,
                              long processingTimeNanos,
                              @Nullable Throwable exception) {}
    }
}
