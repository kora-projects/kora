package ru.tinkoff.kora.micrometer.module;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface MetricsConfig {
    /**
     * @return Metrics format according to the OpenTelemetry standard.
     */
    default TelemetryConfig.MetricsConfig.OpentelemetrySpec opentelemetrySpec() {
        return TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120;
    }
}
