package io.koraframework.http.server.common.telemetry.old;

import io.koraframework.common.Component;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class MyRequest {

    @Component
    public static class MyHttpServerTelemetryFactory implements HttpServerTelemetryFactory {

        private final MeterRegistry meterRegistry;
        private final Tracer tracer;

        public MyHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
            if (meterRegistry == null) {
                meterRegistry = new CompositeMeterRegistry();
            }
            if (tracer == null) {
                tracer = TracerProvider.noop().get("http-server");
            }
            this.meterRegistry = meterRegistry;
            this.tracer = tracer;
        }

        @Override
        public HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig) {
            return new MyHttpServerTelemetry(telemetryConfig, meterRegistry, tracer);
        }
    }

    public static class MyHttpServerTelemetry extends DefaultHttpServerTelemetry {

        public MyHttpServerTelemetry(HttpServerTelemetryConfig config, MeterRegistry meterRegistry, Tracer tracer) {
            super(config, meterRegistry, tracer);
        }

        @Override
        public HttpServerObservation observe(HttpServerRequest request) {
            var span = this.createSpan(request);

            Meter.MeterProvider<Timer> requestDuration = tags -> {
                var real = requestDuration(request);
                return real.withTags(Tags.concat(tags, Tags.of("myKey", request.headers().getFirst("some-request-header"))));
            };
            var activeRequests = this.activeRequests(request);

            return new DefaultHttpServerObservation(config, request, request.requestStartTimeInNanos(), span, logger, requestDuration, activeRequests);
        }
    }
}
