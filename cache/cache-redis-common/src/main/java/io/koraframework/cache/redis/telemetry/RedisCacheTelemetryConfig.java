package io.koraframework.cache.redis.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface RedisCacheTelemetryConfig extends TelemetryConfig {

    @Override
    RedisCacheLoggingConfig logging();

    @Override
    RedisCacheTracingConfig tracing();

    @Override
    RedisCacheMetricsConfig metrics();

    @ConfigMapper
    interface RedisCacheTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigMapper
    interface RedisCacheLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface RedisCacheMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
