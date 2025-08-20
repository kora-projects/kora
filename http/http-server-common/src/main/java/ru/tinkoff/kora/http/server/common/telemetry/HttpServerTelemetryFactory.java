package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;

public interface HttpServerTelemetryFactory {
    @Nullable
    HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}
