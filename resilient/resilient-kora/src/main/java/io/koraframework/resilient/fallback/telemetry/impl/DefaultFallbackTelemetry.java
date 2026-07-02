package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultFallbackTelemetry implements FallbackTelemetry {

    public record TelemetryContext(String name,
                                   FallbackTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {

        public static final TelemetryContext EMPTY = new TelemetryContext("none",
            new $FallbackTelemetryConfig_ConfigValueMapper.FallbackTelemetryConfig_Impl(
                new $FallbackTelemetryConfig_FallbackLoggingConfig_ConfigValueMapper.FallbackLoggingConfig_Defaults(),
                new $FallbackTelemetryConfig_FallbackMetricsConfig_ConfigValueMapper.FallbackMetricsConfig_Defaults(),
                new $FallbackTelemetryConfig_FallbackTracingConfig_ConfigValueMapper.FallbackTracingConfig_Defaults()
            ), false, false, DefaultFallbackTelemetryFactory.NOOP_TRACER, DefaultFallbackTelemetryFactory.NOOP_METER_REGISTRY);
    }

    protected final TelemetryContext context;
    protected final DefaultFallbackLoggerFactory.DefaultFallbackLogger logger;
    protected final DefaultFallbackMetricsFactory.DefaultFallbackMetrics metrics;

    public DefaultFallbackTelemetry(String name,
                                    FallbackTelemetryConfig config,
                                    Tracer tracer,
                                    MeterRegistry meterRegistry,
                                    DefaultFallbackMetricsFactory metricsFactory,
                                    DefaultFallbackLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(name,
            config,
            config.tracing().enabled() && tracer != DefaultFallbackTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultFallbackTelemetryFactory.NOOP_METER_REGISTRY,
            tracer,
            meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public FallbackObservation observe() {
        return new DefaultFallbackObservation(this.context, this.logger, this.metrics);
    }
}
