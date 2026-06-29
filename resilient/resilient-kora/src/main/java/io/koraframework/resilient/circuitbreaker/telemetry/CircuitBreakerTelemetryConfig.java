package io.koraframework.resilient.circuitbreaker.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface CircuitBreakerTelemetryConfig extends TelemetryConfig {

    @Override
    CircuitBreakerLoggingConfig logging();

    @Override
    CircuitBreakerMetricsConfig metrics();

    @Override
    CircuitBreakerTracingConfig tracing();

    @ConfigValueExtractor
    interface CircuitBreakerLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface CircuitBreakerMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface CircuitBreakerTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
