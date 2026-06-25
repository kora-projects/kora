package io.koraframework.cache.redis.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface RedisCacheTelemetryConfig extends TelemetryConfig {

    @Override
    RedisCacheLoggingConfig logging();

    @Override
    RedisCacheTracingConfig tracing();

    @Override
    RedisCacheMetricsConfig metrics();

    @ConfigValueExtractor
    interface RedisCacheTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigValueExtractor
    interface RedisCacheLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface RedisCacheMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
