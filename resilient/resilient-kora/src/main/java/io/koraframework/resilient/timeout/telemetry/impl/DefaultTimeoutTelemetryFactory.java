package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultTimeoutTelemetryFactory implements TimeoutTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-timeout");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultTimeoutLoggerFactory loggerFactory;
    @Nullable
    private final DefaultTimeoutMetricsFactory metricsFactory;

    public DefaultTimeoutTelemetryFactory(@Nullable Tracer tracer,
                                          @Nullable MeterRegistry meterRegistry,
                                          @Nullable DefaultTimeoutLoggerFactory loggerFactory,
                                          @Nullable DefaultTimeoutMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public TimeoutTelemetry get(String name, TimeoutTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopTimeoutTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultTimeoutLoggerFactory.INSTANCE)
            : NoopTimeoutLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultTimeoutMetricsFactory.INSTANCE)
            : NoopTimeoutMetricsFactory.INSTANCE;
        return build(name, config, traceEnabled ? this.tracer : NOOP_TRACER, metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY, metricsFactory, loggerFactory);
    }

    protected TimeoutTelemetry build(String name,
                                     TimeoutTelemetryConfig config,
                                     Tracer tracer,
                                     MeterRegistry meterRegistry,
                                     DefaultTimeoutMetricsFactory metricsFactory,
                                     DefaultTimeoutLoggerFactory loggerFactory) {
        return new DefaultTimeoutTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
