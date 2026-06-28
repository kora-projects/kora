package io.koraframework.cache.redis.telemetry.impl;

import io.koraframework.cache.redis.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

public class DefaultRedisCacheTelemetry implements RedisCacheTelemetry {

    public record TelemetryContext(RedisCacheTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   String cacheConfigPath,
                                   String cacheImplCanonicalName,
                                   String cacheImplSimpleName) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new $RedisCacheTelemetryConfig_ConfigValueExtractor.RedisCacheTelemetryConfig_Impl(
            new $RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor.RedisCacheLoggingConfig_Defaults(),
            new $RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor.RedisCacheTracingConfig_Defaults(),
            new $RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor.RedisCacheMetricsConfig_Defaults()
        ), false, false, DefaultRedisCacheTelemetryFactory.NOOP_METER_REGISTRY, DefaultRedisCacheTelemetryFactory.NOOP_TRACER, "", "", "");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_ORIGIN = "origin";

    protected final TelemetryContext context;
    protected final DefaultRedisCacheLoggerFactory.DefaultRedisCacheLogger logger;
    protected final DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics metrics;

    public DefaultRedisCacheTelemetry(String cacheConfigPath,
                                      Class<?> cacheImpl,
                                      RedisCacheTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultRedisCacheMetricsFactory metricsFactory,
                                      DefaultRedisCacheLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultRedisCacheTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultRedisCacheTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTracingEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            cacheConfigPath,
            cacheImpl.getCanonicalName(),
            cacheImpl.getSimpleName()
        );

        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public RedisCacheObservation observe(String operation) {
        var span = (context.isTracingEnabled())
            ? this.createSpan(operation).startSpan()
            : Span.getInvalid();
        return new DefaultRedisCacheObservation(operation, context, logger, metrics, span);
    }

    protected SpanBuilder createSpan(String operation) {
        var span = this.context.tracer().spanBuilder("cache.operation")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(TAG_OPERATION, operation)
            .setAttribute(SYSTEM_CONFIG_PATH, this.context.cacheConfigPath())
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.cacheImplSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.cacheImplCanonicalName())
            .setAttribute(TAG_ORIGIN, "redis");

        for (var e : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }

        return span;
    }
}
