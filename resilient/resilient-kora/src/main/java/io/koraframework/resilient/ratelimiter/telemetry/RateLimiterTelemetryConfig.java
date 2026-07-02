package io.koraframework.resilient.ratelimiter.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface RateLimiterTelemetryConfig extends TelemetryConfig {

    @Override
    RateLimiterLoggingConfig logging();

    @Override
    RateLimiterMetricsConfig metrics();

    @Override
    RateLimiterTracingConfig tracing();

    @ConfigMapper
    interface RateLimiterLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface RateLimiterMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface RateLimiterTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
