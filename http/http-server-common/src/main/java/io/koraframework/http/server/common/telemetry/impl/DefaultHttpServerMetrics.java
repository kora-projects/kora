package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultHttpServerMetrics {

    private record DurationKey(String method,
                               String pathTemplate,
                               String scheme,
                               String host,
                               @Nullable Class<? extends Throwable> errorType) {}

    private record ActiveRequestsKey(String method, String pathTemplate, String scheme, String host) {}

    private final ConcurrentHashMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ActiveRequestsKey, AtomicLong> activeRequestsCache = new ConcurrentHashMap<>();

    protected final MeterRegistry meterRegistry;
    protected final HttpServerTelemetryConfig.HttpServerMetricsConfig config;

    public DefaultHttpServerMetrics(MeterRegistry meterRegistry, HttpServerTelemetryConfig.HttpServerMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    public void recordStart(HttpServerRequest request) {
        activeRequests(request).incrementAndGet();
    }

    public void recordEnd(HttpServerRequest request, @Nullable Throwable exception, long processingTimeNanos) {
        var errorType = exception == null ? null : exception.getClass();
        var key = new DurationKey(
            request.method(),
            Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
            request.scheme(),
            request.host(),
            errorType
        );
        var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricServerDuration(request, exception).register(meterRegistry));
        meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        activeRequests(request).decrementAndGet();
    }

    protected Timer.Builder createMetricServerDuration(HttpServerRequest request, @Nullable Throwable throwable) {
        var staticTags = new ArrayList<Tag>(5 + this.config.tags().size());

        var errorType = (throwable == null) ? "" : throwable.getClass().getCanonicalName();
        staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
        staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE")));
        staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));
        staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.host()));
        staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));

        for (var tag : config.tags().entrySet()) {
            staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
        }

        return Timer.builder("http.server.request.duration")
            .serviceLevelObjectives(this.config.slo())
            .tags(Tags.of(staticTags));
    }

    protected AtomicLong activeRequests(HttpServerRequest request) {
        var key = new ActiveRequestsKey(
            request.method(),
            Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE"),
            request.scheme(),
            request.host()
        );
        return this.activeRequestsCache.computeIfAbsent(key, _ -> createMetricActiveRequests(request));
    }

    protected AtomicLong createMetricActiveRequests(HttpServerRequest request) {
        var tags = new ArrayList<Tag>(4 + this.config.tags().size());
        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), Objects.requireNonNullElse(request.pathTemplate(), "UNKNOWN_ROUTE")));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.scheme()));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.host()));

        for (var e : this.config.tags().entrySet()) {
            tags.add(Tag.of(e.getKey(), e.getValue()));
        }

        var value = new AtomicLong(0);
        Gauge.builder("http.server.active_requests", value, AtomicLong::get)
            .tags(tags)
            .register(this.meterRegistry);
        return value;
    }
}
