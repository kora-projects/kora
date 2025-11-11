package ru.tinkoff.kora.http.server.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerObservation;

public final class NoopHttpServerObservation implements HttpServerObservation {
    public static final HttpServerObservation INSTANCE = new NoopHttpServerObservation();

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeResultCode(HttpResultCode resultCode) {

    }

    @Override
    public void observeError(Throwable exception) {

    }

    @Override
    public HttpServerRequest observeRequest(HttpServerRequest rq) {
        return rq;
    }

    @Override
    public HttpServerResponse observeResponse(HttpServerResponse rs) {
        return rs;
    }
}
