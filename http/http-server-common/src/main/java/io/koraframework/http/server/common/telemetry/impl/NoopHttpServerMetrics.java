package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;

public final class NoopHttpServerMetrics extends DefaultHttpServerMetrics {

    public static final NoopHttpServerMetrics INSTANCE = new NoopHttpServerMetrics();

    private NoopHttpServerMetrics() {
        super(null, null);
    }

    @Override
    public void recordStart(HttpServerRequest request) {

    }

    @Override
    public void recordEnd(HttpServerRequest request, Throwable exception, long processingTimeNanos) {

    }
}
