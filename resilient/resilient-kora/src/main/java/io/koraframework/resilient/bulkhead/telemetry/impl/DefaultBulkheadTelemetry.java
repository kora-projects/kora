package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.Bulkhead;
import io.koraframework.resilient.bulkhead.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultBulkheadTelemetry implements BulkheadTelemetry {

    public record TelemetryContext(
        String name,
        BulkheadTelemetryConfig config,
        boolean isTraceEnabled,
        boolean isMetricsEnabled,
        Tracer tracer,
        MeterRegistry meterRegistry
    ) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            "none",
            new $BulkheadTelemetryConfig_ConfigValueMapper.BulkheadTelemetryConfig_Impl(
                new $BulkheadTelemetryConfig_BulkheadLoggingConfig_ConfigValueMapper.BulkheadLoggingConfig_Defaults(),
                new $BulkheadTelemetryConfig_BulkheadMetricsConfig_ConfigValueMapper.BulkheadMetricsConfig_Defaults(),
                new $BulkheadTelemetryConfig_BulkheadTracingConfig_ConfigValueMapper.BulkheadTracingConfig_Defaults()
            ), false, false, DefaultBulkheadTelemetryFactory.NOOP_TRACER, DefaultBulkheadTelemetryFactory.NOOP_METER_REGISTRY
        );
    }

    protected final TelemetryContext context;
    protected final DefaultBulkheadLoggerFactory.DefaultBulkheadLogger logger;
    protected final DefaultBulkheadMetricsFactory.DefaultBulkheadMetrics metrics;

    public DefaultBulkheadTelemetry(
        String name,
        BulkheadTelemetryConfig config,
        Tracer tracer,
        MeterRegistry meterRegistry,
        DefaultBulkheadMetricsFactory metricsFactory,
        DefaultBulkheadLoggerFactory loggerFactory
    ) {
        this.context = new TelemetryContext(
            name, config, config.tracing().enabled() && tracer != DefaultBulkheadTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultBulkheadTelemetryFactory.NOOP_METER_REGISTRY, tracer, meterRegistry
        );
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public void register(Bulkhead bulkhead) {
        this.metrics.register(bulkhead);
    }

    @Override
    public BulkheadObservation observe() {
        return new DefaultBulkheadObservation(this.context, this.logger, this.metrics);
    }
}
