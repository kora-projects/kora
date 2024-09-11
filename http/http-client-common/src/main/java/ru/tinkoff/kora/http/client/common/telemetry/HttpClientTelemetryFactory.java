package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientTelemetryFactory {
    @Nullable
    HttpClientTelemetry get(TelemetryConfig config, String clientName);
}
