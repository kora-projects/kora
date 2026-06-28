package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultRateLimiterTelemetryFactory implements RateLimiterTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("resilient-ratelimiter");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultRateLimiterLoggerFactory loggerFactory;
    @Nullable
    private final DefaultRateLimiterMetricsFactory metricsFactory;

    public DefaultRateLimiterTelemetryFactory(@Nullable Tracer tracer,
                                              @Nullable MeterRegistry meterRegistry,
                                              @Nullable DefaultRateLimiterLoggerFactory loggerFactory,
                                              @Nullable DefaultRateLimiterMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public RateLimiterTelemetry get(String name, RateLimiterTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricsEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricsEnabled && !config.logging().enabled()) {
            return NoopRateLimiterTelemetry.INSTANCE;
        }
        var loggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultRateLimiterLoggerFactory.INSTANCE)
            : NoopRateLimiterLoggerFactory.INSTANCE;
        var metricsFactory = metricsEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultRateLimiterMetricsFactory.INSTANCE)
            : NoopRateLimiterMetricsFactory.INSTANCE;
        return build(name, config, traceEnabled ? this.tracer : NOOP_TRACER, metricsEnabled ? this.meterRegistry : NOOP_METER_REGISTRY, metricsFactory, loggerFactory);
    }

    protected RateLimiterTelemetry build(String name,
                                         RateLimiterTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultRateLimiterMetricsFactory metricsFactory,
                                         DefaultRateLimiterLoggerFactory loggerFactory) {
        return new DefaultRateLimiterTelemetry(name, config, NOOP_TRACER, meterRegistry, metricsFactory, loggerFactory);
    }
}
