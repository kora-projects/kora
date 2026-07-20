package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCircuitBreakerLoggerFactory {

    public static final DefaultCircuitBreakerLoggerFactory INSTANCE = new DefaultCircuitBreakerLoggerFactory();

    public DefaultCircuitBreakerLogger create(DefaultCircuitBreakerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(CircuitBreaker.class.getCanonicalName() + "." + context.name());
        return new DefaultCircuitBreakerLogger(logger, context);
    }

    public static class DefaultCircuitBreakerLogger {

        protected final Logger logger;
        protected final DefaultCircuitBreakerTelemetry.TelemetryContext context;

        public DefaultCircuitBreakerLogger(Logger logger, DefaultCircuitBreakerTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartAcquire() {
            if (!logger.isTraceEnabled()) {
                return;
            }
            logger.atTrace()
                .addKeyValue("resilientType", "circuitbreaker")
                .addKeyValue("resilientName", this.context.name())
                .log("CircuitBreaker acquire started...");
        }

        public void logAcquire(CircuitBreaker.State state,
                               CircuitBreakerObservation.CallAcquireStatus callStatus,
                               long processingTimeNanos,
                               @Nullable Throwable exception) {
            if (exception != null) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "circuitbreaker")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("state", state.name())
                    .addKeyValue("callStatus", callStatus.name())
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage())
                    .log("CircuitBreaker acquire failed");
            } else if (callStatus == CircuitBreakerObservation.CallAcquireStatus.REJECTED) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "circuitbreaker")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("state", state.name())
                    .addKeyValue("callStatus", callStatus.name())
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("CircuitBreaker acquire rejected");
            } else if (logger.isDebugEnabled()) {
                logger.atDebug()
                    .addKeyValue("resilientType", "circuitbreaker")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("state", state.name())
                    .addKeyValue("callStatus", callStatus.name())
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("CircuitBreaker acquire recorded");
            }
        }

        public void logStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState) {
            if (!logger.isDebugEnabled()) {
                return;
            }
            logger.atDebug()
                .addKeyValue("resilientType", "circuitbreaker")
                .addKeyValue("resilientName", this.context.name())
                .addKeyValue("previousState", previousState.name())
                .addKeyValue("newState", newState.name())
                .log("CircuitBreaker state changed");
        }

        public void logResult(CircuitBreaker.State state,
                              CircuitBreakerObservation.CallResult callResult,
                              long processingTimeNanos,
                              @Nullable Throwable exception) {
            if (exception != null && callResult == CircuitBreakerObservation.CallResult.FAILURE) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "circuitbreaker")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("state", state.name())
                    .addKeyValue("callResult", callResult.name())
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage())
                    .log("CircuitBreaker call failed");
            } else if (logger.isDebugEnabled()) {
                var event = logger.atDebug()
                    .addKeyValue("resilientType", "circuitbreaker")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("state", state.name())
                    .addKeyValue("callResult", callResult.name())
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
                if (exception != null) {
                    event.addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                        .addKeyValue("exceptionMessage", exception.getMessage());
                }
                event.log("CircuitBreaker call result recorded");
            }
        }
    }

}
