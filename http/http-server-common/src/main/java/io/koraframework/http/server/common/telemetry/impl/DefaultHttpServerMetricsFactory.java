package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpMethod;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultHttpServerMetricsFactory {

    public static final DefaultHttpServerMetricsFactory INSTANCE = new DefaultHttpServerMetricsFactory();

    public DefaultHttpServerMetrics create(DefaultHttpServerTelemetry.TelemetryContext context) {
        return new DefaultHttpServerMetrics(context);
    }

    public static class DefaultHttpServerMetrics {

        public record DurationKey(int statusCode,
                                  String method,
                                  String pathTemplate,
                                  String scheme,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(statusCode, method, pathTemplate, scheme, errorType, tags);
            }
        }

        public record ActiveRequestsKey(String method,
                                        String pathTemplate,
                                        String scheme,
                                        @Nullable Tags extraTags) {

            public ActiveRequestsKey withExtraTags(Tags tags) {
                return new ActiveRequestsKey(method, pathTemplate, scheme, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<ActiveRequestsKey, AtomicLong> activeRequestsCache = new ConcurrentHashMap<>();

        protected final DefaultHttpServerTelemetry.TelemetryContext context;

        public DefaultHttpServerMetrics(DefaultHttpServerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordStart(HttpServerRequest request) {
            createMetricActiveRequestsGaugeCounter(request).incrementAndGet();
        }

        public void recordEnd(HttpServerRequest request,
                              HttpServerResponse response,
                              @Nullable Throwable exception,
                              long processingTimeNanos) {
            var key = createMetricServerDurationKey(request, response, exception);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricServerDuration(key, request, response, exception).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
            createMetricActiveRequestsGaugeCounter(request).decrementAndGet();
        }

        // unknown methods are reported as _OTHER, as OTel semconv requires, so clients can't create unbounded meters
        protected static String metricMethod(String method) {
            return switch (method) {
                case HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE,
                     HttpMethod.CONNECT, HttpMethod.OPTIONS, HttpMethod.TRACE, HttpMethod.PATCH, HttpMethod.QUERY -> method;
                default -> "_OTHER";
            };
        }

        protected DurationKey createMetricServerDurationKey(HttpServerRequest request,
                                                            HttpServerResponse response,
                                                            @Nullable Throwable exception) {
            if (exception instanceof CompletionException ce && ce.getCause() != null) {
                exception = ce.getCause();
            }
            var errorType = exception == null ? null : exception.getClass();
            return new DurationKey(
                response.code(),
                metricMethod(request.method()),
                Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
                request.scheme(),
                errorType,
                null
            );
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricServerDuration(DurationKey metricKey,
                                                           HttpServerRequest request,
                                                           HttpServerResponse response,
                                                           @Nullable Throwable throwable) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size() + extraTags);

            var errorType = (throwable == null) ? "" : Objects.requireNonNullElseGet(throwable.getClass().getCanonicalName(), throwable.getClass()::getName);
            staticTags.add(Tag.of("server.name", this.context.name()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(this.context.port())));
            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), metricKey.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(metricKey.statusCode())));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate()));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));

            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("http.server.request.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }

        protected ActiveRequestsKey createMetricActiveRequestsGaugeKey(HttpServerRequest request) {
            return new ActiveRequestsKey(
                metricMethod(request.method()),
                Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
                request.scheme(),
                null
            );
        }

        protected AtomicLong createMetricActiveRequestsGaugeCounter(HttpServerRequest request) {
            var key = createMetricActiveRequestsGaugeKey(request);
            return this.activeRequestsCache.computeIfAbsent(key, _ -> createMetricActiveRequests(key, request));
        }

        protected AtomicLong createMetricActiveRequests(ActiveRequestsKey metricKey, HttpServerRequest request) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(5 + this.context.config().metrics().tags().size() + extraTags);

            staticTags.add(Tag.of("server.name", this.context.name()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(this.context.port())));
            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), metricKey.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));

            for (var e : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(e.getKey(), e.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            var value = new AtomicLong(0);
            Gauge.builder("http.server.active_requests", value, AtomicLong::get)
                .tags(staticTags)
                .register(this.context.meterRegistry());
            return value;
        }
    }
}
