package ru.tinkoff.kora.micrometer.module;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface MetricsConfig {
    TelemetryConfig.MetricsConfig.OpentelemetrySpec opentelemetrySpec();
}
