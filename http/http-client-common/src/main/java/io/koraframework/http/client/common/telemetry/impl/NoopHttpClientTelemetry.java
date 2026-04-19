package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;

public final class NoopHttpClientTelemetry implements HttpClientTelemetry {

    public static final NoopHttpClientTelemetry INSTANCE = new NoopHttpClientTelemetry();

    private NoopHttpClientTelemetry() {}

    @Override
    public HttpClientObservation observe(HttpClientRequest request) {
        return NoopHttpClientObservation.INSTANCE;
    }
}
