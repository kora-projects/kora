package io.koraframework.cache.redis.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface RedisCacheTelemetryConfig {

    RedisCacheLoggingConfig logging();

    RedisCacheTracingConfig tracing();

    RedisCacheMetricsConfig metrics();

    @ConfigValueExtractor
    interface RedisCacheTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigValueExtractor
    interface RedisCacheLoggingConfig extends TelemetryConfig.LogConfig {}

    @ConfigValueExtractor
    interface RedisCacheMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
