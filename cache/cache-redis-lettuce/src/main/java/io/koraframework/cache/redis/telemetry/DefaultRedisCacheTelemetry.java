package io.koraframework.cache.redis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

public class DefaultRedisCacheTelemetry implements RedisCacheTelemetry {

    public record TelemetryContext(RedisCacheTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Logger logger,
                                   String cacheName,
                                   String cacheImpl) {}

    public static final String SYSTEM_NAME = "system.name";
    public static final String SYSTEM_IMPL = "system.impl";

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_ORIGIN = "origin";

    protected final TelemetryContext context;
    protected final DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics metrics;

    public DefaultRedisCacheTelemetry(String cacheName,
                                      String cacheImpl,
                                      RedisCacheTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultRedisCacheMetricsFactory metricsFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultRedisCacheTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultRedisCacheTelemetryFactory.NOOP_METER_REGISTRY;

        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger(cacheImpl)
            : NOPLogger.NOP_LOGGER;

        this.context = new TelemetryContext(config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            logger,
            cacheName,
            cacheImpl
        );

        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public RedisCacheObservation observe(String operation) {
        var span = (context.config.tracing().enabled())
            ? this.createSpan(operation).startSpan()
            : Span.getInvalid();
        return new DefaultRedisCacheObservation(operation, context, metrics, span);
    }

    protected SpanBuilder createSpan(String operation) {
        var span = this.context.tracer.spanBuilder("cache.operation")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(TAG_OPERATION, operation)
            .setAttribute(SYSTEM_NAME, this.context.cacheName)
            .setAttribute(TAG_ORIGIN, "redis");

        for (var e : this.context.config.tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }

        return span;
    }
}
