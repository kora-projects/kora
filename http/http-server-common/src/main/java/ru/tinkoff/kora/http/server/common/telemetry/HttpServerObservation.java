package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

public interface HttpServerObservation extends Observation {
    void observeResultCode(HttpResultCode resultCode);

    HttpServerRequest observeRequest(HttpServerRequest rq);

    HttpServerResponse observeResponse(HttpServerResponse rs);
}
