package io.koraframework.redis.jedis.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface JedisTelemetryConfig extends TelemetryConfig {
    @Override
    JedisClientLoggingConfig logging();

    @Override
    JedisClientMetricsConfig metrics();

    @Override
    JedisClientTracingConfig tracing();

    @ConfigValueExtractor
    interface JedisClientLoggingConfig extends TelemetryConfig.LogConfig {

    }

    @ConfigValueExtractor
    interface JedisClientMetricsConfig extends TelemetryConfig.MetricsConfig {

    }

    @ConfigValueExtractor
    interface JedisClientTracingConfig extends TelemetryConfig.TracingConfig {

    }
}
