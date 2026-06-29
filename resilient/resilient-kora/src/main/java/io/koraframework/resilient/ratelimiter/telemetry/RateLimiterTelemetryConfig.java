package io.koraframework.resilient.ratelimiter.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface RateLimiterTelemetryConfig extends TelemetryConfig {

    @Override
    RateLimiterLoggingConfig logging();

    @Override
    RateLimiterMetricsConfig metrics();

    @Override
    RateLimiterTracingConfig tracing();

    @ConfigValueExtractor
    interface RateLimiterLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface RateLimiterMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface RateLimiterTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
