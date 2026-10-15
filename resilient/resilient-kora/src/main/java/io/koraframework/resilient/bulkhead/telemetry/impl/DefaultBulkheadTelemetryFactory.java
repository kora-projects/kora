package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetryConfig;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultBulkheadTelemetryFactory implements BulkheadTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-bulkhead");
    public static final MeterRegistry NOOP_METER_REGISTRY = NoopMeterRegistry.INSTANCE;

    @Nullable private final Tracer tracer;
    @Nullable private final MeterRegistry meterRegistry;
    @Nullable private final DefaultBulkheadLoggerFactory loggerFactory;
    @Nullable private final DefaultBulkheadMetricsFactory metricsFactory;

    public DefaultBulkheadTelemetryFactory(
        @Nullable Tracer tracer,
        @Nullable MeterRegistry meterRegistry,
        @Nullable DefaultBulkheadLoggerFactory loggerFactory,
        @Nullable DefaultBulkheadMetricsFactory metricsFactory
    ) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public BulkheadTelemetry get(String name, BulkheadTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopBulkheadTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultBulkheadLoggerFactory.INSTANCE)
            : NoopBulkheadLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultBulkheadMetricsFactory.INSTANCE)
            : NoopBulkheadMetricsFactory.INSTANCE;
        return build(
            name,
            config,
            traceEnabled ? this.tracer : NOOP_TRACER,
            metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY,
            metricsFactory,
            loggerFactory
        );
    }

    protected BulkheadTelemetry build(
        String name,
        BulkheadTelemetryConfig config,
        Tracer tracer,
        MeterRegistry meterRegistry,
        DefaultBulkheadMetricsFactory metricsFactory,
        DefaultBulkheadLoggerFactory loggerFactory
    ) {
        return new DefaultBulkheadTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
