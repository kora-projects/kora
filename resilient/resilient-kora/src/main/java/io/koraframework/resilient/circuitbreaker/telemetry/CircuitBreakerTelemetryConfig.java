package io.koraframework.resilient.circuitbreaker.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface CircuitBreakerTelemetryConfig extends TelemetryConfig {

    @Override
    CircuitBreakerLoggingConfig logging();

    @Override
    CircuitBreakerMetricsConfig metrics();

    @Override
    CircuitBreakerTracingConfig tracing();

    @ConfigMapper
    interface CircuitBreakerLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface CircuitBreakerMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface CircuitBreakerTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
