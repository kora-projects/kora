package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.http.server.common.telemetry.*;
import io.opentelemetry.api.trace.*;

public final class NoopHttpServerObservation implements HttpServerObservation {

    public static final NoopHttpServerObservation INSTANCE = new NoopHttpServerObservation();

    private NoopHttpServerObservation() {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeResultCode(HttpResultCode resultCode) {}

    @Override
    public void observeError(Throwable exception) {}

    @Override
    public HttpServerRequest observeRequest(HttpServerRequest request) {
        return request;
    }

    @Override
    public HttpServerResponse observeResponse(HttpServerResponse response) {
        return response;
    }
}
