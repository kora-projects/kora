package ru.tinkoff.kora.http.server.common.telemetry;

public interface HttpServerTelemetryFactory {
    HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}
