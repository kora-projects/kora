package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultCaffeineCacheTelemetryFactory implements CaffeineCacheTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("caffeine");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultCaffeineCacheLoggerFactory loggerFactory;
    @Nullable
    private final DefaultCaffeineCacheMetricsFactory metricsFactory;

    public DefaultCaffeineCacheTelemetryFactory(@Nullable Tracer tracer,
                                                @Nullable MeterRegistry meterRegistry,
                                                @Nullable DefaultCaffeineCacheLoggerFactory loggerFactory,
                                                @Nullable DefaultCaffeineCacheMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CaffeineCacheTelemetry get(String cacheConfigPath, Class<?> cacheImpl, CaffeineCacheTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopCaffeineCacheTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        final DefaultCaffeineCacheMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultCaffeineCacheMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopCaffeineCacheMetricsFactory.INSTANCE;
        }

        final DefaultCaffeineCacheLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultCaffeineCacheLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopCaffeineCacheLoggerFactory.INSTANCE;
        }

        return build(cacheConfigPath, cacheImpl, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected CaffeineCacheTelemetry build(String cacheConfigPath,
                                           Class<?> cacheImpl,
                                           CaffeineCacheTelemetryConfig config,
                                           Tracer tracer,
                                           MeterRegistry meterRegistry,
                                           DefaultCaffeineCacheMetricsFactory metricsFactory,
                                           DefaultCaffeineCacheLoggerFactory loggerFactory) {
        return new DefaultCaffeineCacheTelemetry(cacheConfigPath, cacheImpl, config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
