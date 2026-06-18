package io.koraframework.http.server.common.telemetry.old;

import io.koraframework.common.Component;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class My {

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

            var requestDuration = this.requestDuration(request);
            var activeRequests = this.activeRequests(request);

            return new MyHttpServerObservation(config, request, request.requestStartTimeInNanos(), span, logger, requestDuration, activeRequests);
        }
    }

    public static class MyHttpServerObservation extends DefaultHttpServerObservation {

        public MyHttpServerObservation(HttpServerTelemetryConfig config, HttpServerRequest request, long requestStartTime, Span span, DefaultHttpServerLogger logger, Meter.MeterProvider<Timer> requestDuration, Function<Tags, AtomicLong> activeRequests) {
            super(config, request, requestStartTime, span, logger, requestDuration, activeRequests);
        }

        @Override
        protected void recordMetrics(long processingTime) {
            if (this.config.metrics().enabled()) {
                var tags = Tags.of(
                    ErrorAttributes.ERROR_TYPE.getKey(), exception == null ? "" : exception.getClass().getCanonicalName(),
                    "myKey", response.headers().getFirst("someHeader")
                );

                this.requestDuration.withTags(tags)
                    .record(processingTime, TimeUnit.NANOSECONDS);
                this.activeRequests.apply(Tags.empty()).decrementAndGet();
            }
        }
    }
}
