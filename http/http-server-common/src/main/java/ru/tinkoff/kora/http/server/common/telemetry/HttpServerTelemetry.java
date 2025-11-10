package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;

public interface HttpServerTelemetry {
    HttpServerObservation observe(PublicApiRequest publicApiRequest, HttpServerRequest request);
}
