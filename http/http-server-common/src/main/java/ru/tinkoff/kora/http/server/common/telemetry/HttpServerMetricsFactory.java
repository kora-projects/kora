package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpServerMetricsFactory {
    @Nullable
    HttpServerMetrics get(TelemetryConfig.MetricsConfig config);
}
