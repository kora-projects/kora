package io.koraframework.http.server.common.telemetry.impl;

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

        public record DurationKey(String method,
                                  String pathTemplate,
                                  String scheme,
                                  String host,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(method, pathTemplate, scheme, host, errorType, tags);
            }
        }

        public record ActiveRequestsKey(String method,
                                        String pathTemplate,
                                        String scheme,
                                        String host,
                                        @Nullable Tags extraTags) {

            public ActiveRequestsKey withExtraTags(Tags tags) {
                return new ActiveRequestsKey(method, pathTemplate, scheme, host, tags);
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

        protected DurationKey createMetricServerDurationKey(HttpServerRequest request,
                                                            HttpServerResponse response,
                                                            @Nullable Throwable exception) {
            if (exception instanceof CompletionException ce && ce.getCause() != null) {
                exception = ce.getCause();
            }
            var errorType = exception == null ? null : exception.getClass();
            return new DurationKey(
                request.method(),
                Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
                request.scheme(),
                request.host(),
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
            var staticTags = new ArrayList<Tag>(5 + this.context.config().metrics().tags().size() + extraTags);

            var errorType = (throwable == null) ? "" : throwable.getClass().getCanonicalName();
            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate()));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.host()));
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
                request.method(),
                Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
                request.scheme(),
                request.host(),
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
            var staticTags = new ArrayList<Tag>(4 + this.context.config().metrics().tags().size() + extraTags);

            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.host()));

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
