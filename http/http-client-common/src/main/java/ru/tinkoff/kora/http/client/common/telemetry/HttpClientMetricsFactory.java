package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientMetricsFactory {
    @Nullable
    HttpClientMetrics get(TelemetryConfig.MetricsConfig metrics, String clientName);
}
