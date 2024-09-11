package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientLoggerFactory {
    @Nullable
    HttpClientLogger get(TelemetryConfig.LogConfig logging, String clientName);
}
