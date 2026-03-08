package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.router.PublicApiRequest;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerObservation;

public class NoopHttpServerTelemetry implements HttpServerTelemetry {
    public static final NoopHttpServerTelemetry INSTANCE = new NoopHttpServerTelemetry();

    @Override
    public HttpServerObservation observe(PublicApiRequest publicApiRequest, HttpServerRequest request) {
        return NoopHttpServerObservation.INSTANCE;
    }
}
