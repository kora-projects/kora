package io.koraframework.database.common.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface DatabaseTelemetryConfig extends TelemetryConfig {

    DatabaseLoggingConfig logging();

    DatabaseMetricsConfig metrics();

    DatabaseTracingConfig tracing();

    @ConfigMapper
    interface DatabaseLoggingConfig extends LoggingConfig {}

    @ConfigMapper
    interface DatabaseMetricsConfig extends MetricsConfig {

        default boolean driverMetrics() {
            return true;
        }
    }

    @ConfigMapper
    interface DatabaseTracingConfig extends TracingConfig {}
}
