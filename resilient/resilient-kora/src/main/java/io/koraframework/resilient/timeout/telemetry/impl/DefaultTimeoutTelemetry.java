package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

import java.time.Duration;

public class DefaultTimeoutTelemetry implements TimeoutTelemetry {

    public record TelemetryContext(String name,
                                   TimeoutTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {

        public static final TelemetryContext EMPTY = new TelemetryContext("none",
            new $TimeoutTelemetryConfig_ConfigValueExtractor.TimeoutTelemetryConfig_Impl(
                new $TimeoutTelemetryConfig_TimeoutLoggingConfig_ConfigValueExtractor.TimeoutLoggingConfig_Defaults(),
                new $TimeoutTelemetryConfig_TimeoutMetricsConfig_ConfigValueExtractor.TimeoutMetricsConfig_Defaults(),
                new $TimeoutTelemetryConfig_TimeoutTracingConfig_ConfigValueExtractor.TimeoutTracingConfig_Defaults()
            ), false, false, DefaultTimeoutTelemetryFactory.NOOP_TRACER, DefaultTimeoutTelemetryFactory.NOOP_METER_REGISTRY);
    }

    protected final TelemetryContext context;
    protected final DefaultTimeoutLoggerFactory.DefaultTimeoutLogger logger;
    protected final DefaultTimeoutMetricsFactory.DefaultTimeoutMetrics metrics;

    public DefaultTimeoutTelemetry(String name,
                                   TimeoutTelemetryConfig config,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry,
                                   DefaultTimeoutMetricsFactory metricsFactory,
                                   DefaultTimeoutLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(name,
            config,
            config.tracing().enabled() && tracer != DefaultTimeoutTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultTimeoutTelemetryFactory.NOOP_METER_REGISTRY,
            tracer,
            meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public TimeoutObservation observe(Duration timeToWait) {
        return new DefaultTimeoutObservation(timeToWait.toNanos(), this.context, this.logger, this.metrics);
    }
}
