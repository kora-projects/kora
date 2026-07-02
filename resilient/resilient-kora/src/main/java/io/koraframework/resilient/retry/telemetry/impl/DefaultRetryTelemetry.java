package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultRetryTelemetry implements RetryTelemetry {

    public record TelemetryContext(String name,
                                   RetryTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {

        public static final TelemetryContext EMPTY = new TelemetryContext("none",
            new $RetryTelemetryConfig_ConfigValueMapper.RetryTelemetryConfig_Impl(
                new $RetryTelemetryConfig_RetryLoggingConfig_ConfigValueMapper.RetryLoggingConfig_Defaults(),
                new $RetryTelemetryConfig_RetryMetricsConfig_ConfigValueMapper.RetryMetricsConfig_Defaults(),
                new $RetryTelemetryConfig_RetryTracingConfig_ConfigValueMapper.RetryTracingConfig_Defaults()
            ), false, false, DefaultRetryTelemetryFactory.NOOP_TRACER, DefaultRetryTelemetryFactory.NOOP_METER_REGISTRY);
    }

    protected final TelemetryContext context;
    protected final DefaultRetryLoggerFactory.DefaultRetryLogger logger;
    protected final DefaultRetryMetricsFactory.DefaultRetryMetrics metrics;

    public DefaultRetryTelemetry(String name,
                                 RetryTelemetryConfig config,
                                 Tracer tracer,
                                 MeterRegistry meterRegistry,
                                 DefaultRetryMetricsFactory metricsFactory,
                                 DefaultRetryLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(name,
            config,
            config.tracing().enabled() && tracer != DefaultRetryTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultRetryTelemetryFactory.NOOP_METER_REGISTRY,
            tracer,
            meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public RetryObservation observe() {
        return new DefaultRetryObservation(this.context, this.logger, this.metrics);
    }
}
