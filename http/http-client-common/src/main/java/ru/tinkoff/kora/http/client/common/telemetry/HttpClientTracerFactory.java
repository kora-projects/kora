package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientTracerFactory {
    @Nullable
    HttpClientTracer get(TelemetryConfig.TracingConfig tracing, String clientName);
}
