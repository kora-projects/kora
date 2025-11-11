package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;
import ru.tinkoff.kora.http.server.common.telemetry.impl.NoopHttpServerObservation;

public class NoopHttpServerTelemetry implements HttpServerTelemetry {
    public static final NoopHttpServerTelemetry INSTANCE = new NoopHttpServerTelemetry();

    @Override
    public HttpServerObservation observe(PublicApiRequest publicApiRequest, HttpServerRequest request) {
        return NoopHttpServerObservation.INSTANCE;
    }
}
