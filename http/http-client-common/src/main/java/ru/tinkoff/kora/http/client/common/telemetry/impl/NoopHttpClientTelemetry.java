package ru.tinkoff.kora.http.client.common.telemetry.impl;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientObservation;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

public class NoopHttpClientTelemetry implements HttpClientTelemetry {
    public static final NoopHttpClientTelemetry INSTANCE = new NoopHttpClientTelemetry();

    @Override
    public HttpClientObservation observe(HttpClientRequest request) {
        return NoopHttpClientObservation.INSTANCE;
    }
}
