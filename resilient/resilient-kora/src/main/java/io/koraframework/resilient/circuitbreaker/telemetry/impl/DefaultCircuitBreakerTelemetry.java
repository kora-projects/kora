package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultCircuitBreakerTelemetry implements CircuitBreakerTelemetry {

    public record TelemetryContext(String name,
                                   CircuitBreakerTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {

        public static final TelemetryContext EMPTY = new TelemetryContext("none",
            new $CircuitBreakerTelemetryConfig_ConfigValueExtractor.CircuitBreakerTelemetryConfig_Impl(
                new $CircuitBreakerTelemetryConfig_CircuitBreakerLoggingConfig_ConfigValueExtractor.CircuitBreakerLoggingConfig_Defaults(),
                new $CircuitBreakerTelemetryConfig_CircuitBreakerMetricsConfig_ConfigValueExtractor.CircuitBreakerMetricsConfig_Defaults(),
                new $CircuitBreakerTelemetryConfig_CircuitBreakerTracingConfig_ConfigValueExtractor.CircuitBreakerTracingConfig_Defaults()
            ), false, false, DefaultCircuitBreakerTelemetryFactory.NOOP_TRACER, DefaultCircuitBreakerTelemetryFactory.NOOP_METER_REGISTRY);
    }

    protected final TelemetryContext context;
    protected final DefaultCircuitBreakerLoggerFactory.DefaultCircuitBreakerLogger logger;
    protected final DefaultCircuitBreakerMetricsFactory.DefaultCircuitBreakerMetrics metrics;

    public DefaultCircuitBreakerTelemetry(String name,
                                          CircuitBreakerTelemetryConfig config,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          DefaultCircuitBreakerMetricsFactory metricsFactory,
                                          DefaultCircuitBreakerLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(name,
            config,
            config.tracing().enabled() && tracer != DefaultCircuitBreakerTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultCircuitBreakerTelemetryFactory.NOOP_METER_REGISTRY,
            tracer,
            meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public CircuitBreakerObservation observe() {
        return new DefaultCircuitBreakerObservation(this.context, this.logger, this.metrics);
    }
}
