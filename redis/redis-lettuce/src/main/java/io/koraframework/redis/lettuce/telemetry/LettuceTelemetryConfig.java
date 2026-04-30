package io.koraframework.redis.lettuce.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface LettuceTelemetryConfig {

    LettuceLoggingConfig logging();

    LettuceMetricsConfig metrics();

    @ConfigValueExtractor
    interface LettuceMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface LettuceLoggingConfig extends TelemetryConfig.LogConfig {}
}
