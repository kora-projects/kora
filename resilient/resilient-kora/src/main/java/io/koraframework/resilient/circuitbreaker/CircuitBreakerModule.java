package io.koraframework.resilient.circuitbreaker;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerLoggerFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerMetricsFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface CircuitBreakerModule {

    @DefaultComponent
    default CircuitBreakerTelemetryFactory defaultCircuitBreakerTelemetryFactory(@Nullable Tracer tracer,
                                                                                 @Nullable MeterRegistry meterRegistry,
                                                                                 @Nullable DefaultCircuitBreakerLoggerFactory loggerFactory,
                                                                                 @Nullable DefaultCircuitBreakerMetricsFactory metricsFactory) {
        return new DefaultCircuitBreakerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default CircuitBreakerPredicate defaultCircuitBreakerFailurePredicate() {
        return new KoraCircuitBreakerPredicate();
    }
}
