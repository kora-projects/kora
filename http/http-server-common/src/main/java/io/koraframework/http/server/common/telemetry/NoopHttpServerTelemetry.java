package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerObservation;

public final class NoopHttpServerTelemetry implements HttpServerTelemetry {

    public static final NoopHttpServerTelemetry INSTANCE = new NoopHttpServerTelemetry();

    @Override
    public HttpServerObservation observe(HttpServerRequest request) {
        return NoopHttpServerObservation.INSTANCE;
    }
}
