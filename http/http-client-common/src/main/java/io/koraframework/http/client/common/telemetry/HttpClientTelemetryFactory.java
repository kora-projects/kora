package io.koraframework.http.client.common.telemetry;

public interface HttpClientTelemetryFactory {

    HttpClientTelemetry get(String clientConfigPath, String clientCanonicalName, HttpClientTelemetryConfig config);
}
