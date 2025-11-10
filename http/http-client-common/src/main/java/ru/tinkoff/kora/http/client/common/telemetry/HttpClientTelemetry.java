package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

public interface HttpClientTelemetry {
    HttpClientObservation observe(HttpClientRequest request);
}
