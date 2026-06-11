package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import org.jspecify.annotations.Nullable;

public final class NoopHttpServerMetricsFactory extends DefaultHttpServerMetricsFactory {

    public static final NoopHttpServerMetricsFactory INSTANCE = new NoopHttpServerMetricsFactory();

    private NoopHttpServerMetricsFactory() {}

    @Override
    public DefaultHttpServerMetrics create(DefaultHttpServerTelemetry.TelemetryContext context) {
        return NoopHttpServerMetrics.INSTANCE;
    }

    public static final class NoopHttpServerMetrics extends DefaultHttpServerMetrics {

        public static final NoopHttpServerMetrics INSTANCE = new NoopHttpServerMetrics();

        private NoopHttpServerMetrics() {
            super(DefaultHttpServerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordStart(HttpServerRequest request) {

        }

        @Override
        public void recordEnd(HttpServerRequest request, HttpServerResponse response, @Nullable Throwable exception, long processingTimeNanos) {

        }
    }
}
