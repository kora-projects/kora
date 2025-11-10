package ru.tinkoff.kora.http.client.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientObservation;

public class NoopHttpClientObservation implements HttpClientObservation {
    public static final NoopHttpClientObservation INSTANCE = new NoopHttpClientObservation();

    @Override
    public HttpClientRequest observeRequest(HttpClientRequest rq) {
        return rq;
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse rs) {
        return rs;
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
