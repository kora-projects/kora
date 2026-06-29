package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultRetryTelemetryFactory implements RetryTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-retry");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultRetryLoggerFactory loggerFactory;
    @Nullable
    private final DefaultRetryMetricsFactory metricsFactory;

    public DefaultRetryTelemetryFactory(@Nullable Tracer tracer,
                                        @Nullable MeterRegistry meterRegistry,
                                        @Nullable DefaultRetryLoggerFactory loggerFactory,
                                        @Nullable DefaultRetryMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public RetryTelemetry get(String name, RetryTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopRetryTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultRetryLoggerFactory.INSTANCE)
            : NoopRetryLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultRetryMetricsFactory.INSTANCE)
            : NoopRetryMetricsFactory.INSTANCE;
        return build(name, config, traceEnabled ? this.tracer : NOOP_TRACER, metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY, metricsFactory, loggerFactory);
    }

    protected RetryTelemetry build(String name,
                                   RetryTelemetryConfig config,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry,
                                   DefaultRetryMetricsFactory metricsFactory,
                                   DefaultRetryLoggerFactory loggerFactory) {
        return new DefaultRetryTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
