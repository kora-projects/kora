package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.request.HttpServerRequest;

public interface HttpServerTelemetry {
    HttpServerObservation observe(HttpServerRequest request);
}
