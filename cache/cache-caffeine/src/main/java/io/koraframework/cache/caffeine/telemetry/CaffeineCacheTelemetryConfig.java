package io.koraframework.cache.caffeine.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface CaffeineCacheTelemetryConfig extends TelemetryConfig {

    @Override
    CaffeineCacheLoggingConfig logging();

    @Override
    CaffeineCacheTracingConfig tracing();

    @Override
    CaffeineCacheMetricsConfig metrics();

    @ConfigMapper
    interface CaffeineCacheTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigMapper
    interface CaffeineCacheLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface CaffeineCacheMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
