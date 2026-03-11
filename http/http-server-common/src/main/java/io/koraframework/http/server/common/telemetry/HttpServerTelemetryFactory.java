package io.koraframework.http.server.common.telemetry;

public interface HttpServerTelemetryFactory {
    HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}
