package io.koraframework.http.server.common.telemetry.old;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;

public final class NoopHttpServerTelemetry implements HttpServerTelemetry {

    public static final NoopHttpServerTelemetry INSTANCE = new NoopHttpServerTelemetry();

    @Override
    public HttpServerObservation observe(HttpServerRequest request) {
        return NoopHttpServerObservation.INSTANCE;
    }
}
