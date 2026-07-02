package io.koraframework.redis.lettuce.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface LettuceTelemetryConfig {

    LettuceLoggingConfig logging();

    LettuceMetricsConfig metrics();

    @ConfigMapper
    interface LettuceMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface LettuceLoggingConfig extends TelemetryConfig.LoggingConfig {}
}
