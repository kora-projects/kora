package io.koraframework.http.client.common.telemetry;

public interface HttpClientTelemetryFactory {
    HttpClientTelemetry get(HttpClientTelemetryConfig config, String clientName);
}
