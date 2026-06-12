package io.koraframework.cache.redis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultRedisCacheTelemetryFactory implements RedisCacheTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("redis-lettuce");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultRedisCacheLoggerFactory loggerFactory;
    @Nullable
    private final DefaultRedisCacheMetricsFactory metricsFactory;

    public DefaultRedisCacheTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultRedisCacheLoggerFactory loggerFactory,
                                             @Nullable DefaultRedisCacheMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public RedisCacheTelemetry get(String cacheName, String cacheImpl, RedisCacheTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopRedisCacheTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        final DefaultRedisCacheMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultRedisCacheMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopRedisCacheMetricsFactory.INSTANCE;
        }

        final DefaultRedisCacheLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultRedisCacheLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopRedisCacheLoggerFactory.INSTANCE;
        }

        return new DefaultRedisCacheTelemetry(cacheName, cacheImpl, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }
}
