package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultCircuitBreakerTelemetryFactory implements CircuitBreakerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-circuitbreaker");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultCircuitBreakerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultCircuitBreakerMetricsFactory metricsFactory;

    public DefaultCircuitBreakerTelemetryFactory(@Nullable Tracer tracer,
                                                 @Nullable MeterRegistry meterRegistry,
                                                 @Nullable DefaultCircuitBreakerLoggerFactory loggerFactory,
                                                 @Nullable DefaultCircuitBreakerMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CircuitBreakerTelemetry get(String name, CircuitBreakerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopCircuitBreakerTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultCircuitBreakerLoggerFactory.INSTANCE)
            : NoopCircuitBreakerLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultCircuitBreakerMetricsFactory.INSTANCE)
            : NoopCircuitBreakerMetricsFactory.INSTANCE;
        return build(name, config, traceEnabled ? this.tracer : NOOP_TRACER, metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY, metricsFactory, loggerFactory);
    }

    protected CircuitBreakerTelemetry build(String name,
                                            CircuitBreakerTelemetryConfig config,
                                            Tracer tracer,
                                            MeterRegistry meterRegistry,
                                            DefaultCircuitBreakerMetricsFactory metricsFactory,
                                            DefaultCircuitBreakerLoggerFactory loggerFactory) {
        return new DefaultCircuitBreakerTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
