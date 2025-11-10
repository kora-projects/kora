package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public interface HttpClientObservation extends Observation {
    HttpClientRequest observeRequest(HttpClientRequest rq);

    HttpClientResponse observeResponse(HttpClientResponse rs);
}
