package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

public final class NoopHttpClientMetrics extends DefaultHttpClientMetrics {

    public static final NoopHttpClientMetrics INSTANCE = new NoopHttpClientMetrics();

    private NoopHttpClientMetrics() {
        super(null, null, null, null);
    }

    @Override
    public void recordFailure(HttpClientRequest rq, Throwable exception, long processingTimeNanos) {

    }

    @Override
    public void recordSuccess(HttpClientRequest rq, HttpClientResponse rs, long processingTimeNanos) {

    }
}
