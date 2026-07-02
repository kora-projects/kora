package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultRateLimiterTelemetry implements RateLimiterTelemetry {

    public record TelemetryContext(String name,
                                   RateLimiterTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {

        public static final TelemetryContext EMPTY = new TelemetryContext("none",
            new $RateLimiterTelemetryConfig_ConfigValueMapper.RateLimiterTelemetryConfig_Impl(
                new $RateLimiterTelemetryConfig_RateLimiterLoggingConfig_ConfigValueMapper.RateLimiterLoggingConfig_Defaults(),
                new $RateLimiterTelemetryConfig_RateLimiterMetricsConfig_ConfigValueMapper.RateLimiterMetricsConfig_Defaults(),
                new $RateLimiterTelemetryConfig_RateLimiterTracingConfig_ConfigValueMapper.RateLimiterTracingConfig_Defaults()
            ), false, false, DefaultRateLimiterTelemetryFactory.NOOP_TRACER, DefaultRateLimiterTelemetryFactory.NOOP_METER_REGISTRY);
    }

    protected final TelemetryContext context;
    protected final DefaultRateLimiterLoggerFactory.DefaultRateLimiterLogger logger;
    protected final DefaultRateLimiterMetricsFactory.DefaultRateLimiterMetrics metrics;

    public DefaultRateLimiterTelemetry(String name,
                                       RateLimiterTelemetryConfig config,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultRateLimiterMetricsFactory metricsFactory,
                                       DefaultRateLimiterLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(name,
            config,
            config.tracing().enabled() && tracer != DefaultRateLimiterTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultRateLimiterTelemetryFactory.NOOP_METER_REGISTRY,
            tracer,
            meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public RateLimiterObservation observe() {
        return new DefaultRateLimiterObservation(this.context, this.logger, this.metrics);
    }
}
