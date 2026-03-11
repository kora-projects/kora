package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.router.PublicApiRequest;

public interface HttpServerTelemetry {
    HttpServerObservation observe(PublicApiRequest publicApiRequest, HttpServerRequest request);
}
