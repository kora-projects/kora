package io.koraframework.http.server.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;

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
    public HttpServerRequest observeRequest(HttpServerRequest request) {
        return request;
    }

    @Override
    public HttpServerResponse observeResponse(HttpServerResponse response) {
        return response;
    }
}
