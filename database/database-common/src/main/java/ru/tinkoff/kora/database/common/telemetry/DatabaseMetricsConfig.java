package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Map;

@ConfigValueExtractor
public interface DatabaseMetricsConfig extends TelemetryConfig.MetricsConfig {

    default Map<String, String> tags() {
        return Map.of();
    }
}
