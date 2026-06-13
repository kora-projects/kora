package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public final class NoopCamundaRestMetricsFactory extends DefaultCamundaRestMetricsFactory {

    public static final NoopCamundaRestMetricsFactory INSTANCE = new NoopCamundaRestMetricsFactory();

    private NoopCamundaRestMetricsFactory() {}

    @Override
    public DefaultCamundaRestMetrics create(DefaultCamundaRestTelemetry.TelemetryContext context) {
        return NoopCamundaRestMetrics.INSTANCE;
    }

    public static final class NoopCamundaRestMetrics extends DefaultCamundaRestMetrics {

        public static final NoopCamundaRestMetrics INSTANCE = new NoopCamundaRestMetrics();

        private NoopCamundaRestMetrics() {
            super(null);
        }

        @Override
        public void recordDuration(HttpServerExchange exchange,
                                   @Nullable String route,
                                   int statusCode,
                                   @Nullable HttpResultCode resultCode,
                                   @Nullable Throwable throwable,
                                   long processingTimeNanos) {

        }

        @Override
        public void recordActive(HttpServerExchange exchange, String route, int delta) {

        }
    }
}
