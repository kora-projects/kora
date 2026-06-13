package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public final class NoopOperatonRestMetricsFactory extends DefaultOperatonRestMetricsFactory {

    public static final NoopOperatonRestMetricsFactory INSTANCE = new NoopOperatonRestMetricsFactory();

    private NoopOperatonRestMetricsFactory() {}

    @Override
    public DefaultOperatonRestMetrics create(DefaultOperatonRestTelemetry.TelemetryContext context) {
        return NoopOperatonRestMetrics.INSTANCE;
    }

    public static final class NoopOperatonRestMetrics extends DefaultOperatonRestMetrics {

        public static final NoopOperatonRestMetrics INSTANCE = new NoopOperatonRestMetrics();

        private NoopOperatonRestMetrics() {
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
