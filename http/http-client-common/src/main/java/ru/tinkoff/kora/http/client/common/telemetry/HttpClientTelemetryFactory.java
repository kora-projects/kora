package ru.tinkoff.kora.http.client.common.telemetry;

public interface HttpClientTelemetryFactory {
    HttpClientTelemetry get(HttpClientTelemetryConfig config, String clientName);
}
