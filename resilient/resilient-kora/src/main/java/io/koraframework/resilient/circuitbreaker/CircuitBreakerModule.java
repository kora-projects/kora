package io.koraframework.resilient.circuitbreaker;

import io.koraframework.application.graph.All;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerLoggerFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerMetricsFactory;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.DefaultCircuitBreakerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface CircuitBreakerModule {

    default CircuitBreakerConfig koraCircuitBreakerConfig(Config config, ConfigValueExtractor<CircuitBreakerConfig> extractor) {
        return extractor.extractOrThrow(config.get("resilient"));
    }

    default CircuitBreakerManager koraCircuitBreakerManager(CircuitBreakerConfig config,
                                                            All<CircuitBreakerPredicate> failurePredicates,
                                                            CircuitBreakerTelemetryFactory telemetryFactory) {
        return new KoraCircuitBreakerManager(config, failurePredicates, telemetryFactory);
    }

    @DefaultComponent
    default CircuitBreakerTelemetryFactory defaultCircuitBreakerTelemetryFactory(@Nullable Tracer tracer,
                                                                                 @Nullable MeterRegistry meterRegistry,
                                                                                 @Nullable DefaultCircuitBreakerLoggerFactory loggerFactory,
                                                                                 @Nullable DefaultCircuitBreakerMetricsFactory metricsFactory) {
        return new DefaultCircuitBreakerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    default CircuitBreakerPredicate defaultCircuitBreakerFailurePredicate() {
        return new KoraCircuitBreakerPredicate();
    }
}
