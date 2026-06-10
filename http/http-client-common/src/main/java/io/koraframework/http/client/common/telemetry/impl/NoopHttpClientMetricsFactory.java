package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

public final class NoopHttpClientMetricsFactory extends DefaultHttpClientMetricsFactory {

    public static final NoopHttpClientMetricsFactory INSTANCE = new NoopHttpClientMetricsFactory();

    private NoopHttpClientMetricsFactory() {}

    @Override
    public DefaultHttpClientMetrics create(DefaultHttpClientTelemetry.TelemetryContext context) {
        return NoopHttpClientMetrics.INSTANCE;
    }

    public static final class NoopHttpClientMetrics extends DefaultHttpClientMetrics {

        public static final NoopHttpClientMetrics INSTANCE = new NoopHttpClientMetrics();

        private NoopHttpClientMetrics() {
            super(DefaultHttpClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordFailure(HttpClientRequest rq, Throwable exception, long processingTimeNanos) {

        }

        @Override
        public void recordSuccess(HttpClientRequest rq, HttpClientResponse rs, long processingTimeNanos) {

        }
    }
}
