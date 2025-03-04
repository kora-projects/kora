package ru.tinkoff.kora.cache.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CacheTelemetryFactory {

    CacheTelemetry get(TelemetryConfig telemetryConfig, CacheTelemetryArgs args);
}
