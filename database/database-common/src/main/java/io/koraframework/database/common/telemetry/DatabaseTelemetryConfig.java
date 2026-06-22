package io.koraframework.database.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface DatabaseTelemetryConfig extends TelemetryConfig {

    DatabaseLoggingConfig logging();

    DatabaseMetricsConfig metrics();

    DatabaseTracingConfig tracing();

    @ConfigValueExtractor
    interface DatabaseLoggingConfig extends LoggingConfig {}

    @ConfigValueExtractor
    interface DatabaseMetricsConfig extends MetricsConfig {

        default boolean driverMetrics() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface DatabaseTracingConfig extends TracingConfig {}
}
