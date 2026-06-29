package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultFallbackTelemetryFactory implements FallbackTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-fallback");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultFallbackLoggerFactory loggerFactory;
    @Nullable
    private final DefaultFallbackMetricsFactory metricsFactory;

    public DefaultFallbackTelemetryFactory(@Nullable Tracer tracer,
                                           @Nullable MeterRegistry meterRegistry,
                                           @Nullable DefaultFallbackLoggerFactory loggerFactory,
                                           @Nullable DefaultFallbackMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public FallbackTelemetry get(String name, FallbackTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopFallbackTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultFallbackLoggerFactory.INSTANCE)
            : NoopFallbackLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultFallbackMetricsFactory.INSTANCE)
            : NoopFallbackMetricsFactory.INSTANCE;
        return build(name, config, traceEnabled ? this.tracer : NOOP_TRACER, metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY, metricsFactory, loggerFactory);
    }

    protected FallbackTelemetry build(String name,
                                      FallbackTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultFallbackMetricsFactory metricsFactory,
                                      DefaultFallbackLoggerFactory loggerFactory) {
        return new DefaultFallbackTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
