package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

public class DefaultCaffeineCacheTelemetry implements CaffeineCacheTelemetry {

    public record TelemetryContext(CaffeineCacheTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   String cacheConfigPath,
                                   String cacheImplCanonicalName,
                                   String cacheImplSimpleName) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new $CaffeineCacheTelemetryConfig_ConfigValueMapper.CaffeineCacheTelemetryConfig_Impl(
            new $CaffeineCacheTelemetryConfig_CaffeineCacheLoggingConfig_ConfigValueMapper.CaffeineCacheLoggingConfig_Defaults(),
            new $CaffeineCacheTelemetryConfig_CaffeineCacheTracingConfig_ConfigValueMapper.CaffeineCacheTracingConfig_Defaults(),
            new $CaffeineCacheTelemetryConfig_CaffeineCacheMetricsConfig_ConfigValueMapper.CaffeineCacheMetricsConfig_Defaults()
        ), false, false, DefaultCaffeineCacheTelemetryFactory.NOOP_METER_REGISTRY, DefaultCaffeineCacheTelemetryFactory.NOOP_TRACER, "", "", "");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultCaffeineCacheLoggerFactory.DefaultCaffeineCacheLogger logger;

    public DefaultCaffeineCacheTelemetry(String cacheConfigPath,
                                         Class<?> cacheImpl,
                                         CaffeineCacheTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultCaffeineCacheMetricsFactory metricsFactory,
                                         DefaultCaffeineCacheLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultCaffeineCacheTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultCaffeineCacheTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTracingEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            cacheConfigPath,
            cacheImpl.getCanonicalName(),
            cacheImpl.getSimpleName()
        );

        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public CaffeineCacheObservation observe(Operation operation) {
        return new DefaultCaffeineCacheObservation(operation, logger);
    }
}
