package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopHttpClientObservation implements HttpClientObservation {

    public static final NoopHttpClientObservation INSTANCE = new NoopHttpClientObservation();

    private NoopHttpClientObservation() {}

    @Override
    public HttpClientRequest observeRequest(HttpClientRequest request) {
        return request;
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse response) {
        return response;
    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
