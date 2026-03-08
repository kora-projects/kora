package io.koraframework.database.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface DatabaseTelemetryConfig extends TelemetryConfig {
    DatabaseLogConfig logging();

    DatabaseTracingConfig tracing();

    DatabaseMetricsConfig metrics();

    @ConfigValueExtractor
    interface DatabaseLogConfig extends LogConfig {
    }

    @ConfigValueExtractor
    interface DatabaseTracingConfig extends TracingConfig {
    }

    @ConfigValueExtractor
    interface DatabaseMetricsConfig extends MetricsConfig {
        default boolean driverMetrics() {
            return true;
        }
    }
}
