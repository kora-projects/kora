package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
