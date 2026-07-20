package io.koraframework.http.server.common.telemetry;

public interface HttpServerTelemetryFactory {

    HttpServerTelemetry get(String name, int port, HttpServerTelemetryConfig telemetryConfig);
}
