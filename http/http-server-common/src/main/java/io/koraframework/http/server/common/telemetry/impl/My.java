package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.common.Component;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

public class My {

    @Component
    public static class MyHttpServerMetricsFactory extends DefaultHttpServerMetricsFactory {

        @Override
        public DefaultHttpServerMetrics create(DefaultHttpServerTelemetry.TelemetryContext context) {
            return new MyHttpServerMetrics(context);
        }
    }

    public static class MyHttpServerMetrics extends DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics {

        public MyHttpServerMetrics(DefaultHttpServerTelemetry.TelemetryContext context) {
            super(context);
        }

        @Override
        protected DurationKey createMetricServerDurationKey(HttpServerRequest request, HttpServerResponse response, @Nullable Throwable exception) {
            return super.createMetricServerDurationKey(request, response, exception)
                .withExtraTags(Tags.of("myKey", response.headers().getFirst("someHeader")));
        }
    }
}
