package io.koraframework.http.client.common.telemetry;

public interface HttpClientTelemetryFactory {

    HttpClientTelemetry get(String clientName, String clientImpl, HttpClientTelemetryConfig config);
}
