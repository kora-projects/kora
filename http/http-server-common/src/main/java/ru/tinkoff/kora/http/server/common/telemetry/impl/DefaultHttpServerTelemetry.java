package ru.tinkoff.kora.http.server.common.telemetry.impl;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerObservation;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class DefaultHttpServerTelemetry implements HttpServerTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));
    private final HttpServerTelemetryConfig config;
    private final Tracer tracer;
    private final DefaultHttpServerLogger logger;
    private final MeterRegistry meterRegistry;

    private record DurationCacheKey(String method, String pathTemplate, String scheme, String host) {}
    private final ConcurrentHashMap<DurationCacheKey, ConcurrentHashMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationCacheKey, ConcurrentHashMap<Tags, AtomicLong>> activeRequestsCache = new ConcurrentHashMap<>();

    public DefaultHttpServerTelemetry(HttpServerTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, DefaultHttpServerLogger logger) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.logger = logger;
    }

    public DefaultHttpServerTelemetry(HttpServerTelemetryConfig config, MeterRegistry meterRegistry, Tracer tracer) {
        var logger = new DefaultHttpServerLogger(config.logging());
        this(config, tracer, meterRegistry, logger);
    }

    @Override
    public HttpServerObservation observe(PublicApiRequest publicApiRequest, HttpServerRequest request) {
        var span = this.createSpan(request.route(), publicApiRequest);
        var requestDuration = this.requestDuration(publicApiRequest, request);
        var activeRequests = this.activeRequests(publicApiRequest, request);

        return new DefaultHttpServerObservation(config, request, publicApiRequest.requestStartTime(), span, logger, requestDuration, activeRequests);
    }

    protected Meter.MeterProvider<Timer> requestDuration(PublicApiRequest publicApiRequest, HttpServerRequest request) {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        var key = new DurationCacheKey(
            publicApiRequest.method(),
            Objects.requireNonNullElse(request.route(), "UNKNOWN_ROUTE"),
            publicApiRequest.scheme(),
            publicApiRequest.hostName()
        );
        var baseCache = this.durationCache.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        return tags -> baseCache.computeIfAbsent(Tags.of(tags), t -> requestDuration(publicApiRequest, request, t));
    }

    protected Function<Tags, AtomicLong> activeRequests(PublicApiRequest publicApiRequest, HttpServerRequest request) {
        if (!this.config.metrics().enabled()) {
            return _ -> new AtomicLong(0);
        }
        var key = new DurationCacheKey(
            publicApiRequest.method(),
            Objects.requireNonNullElse(request.route(), "UNKNOWN_ROUTE"),
            publicApiRequest.scheme(),
            publicApiRequest.hostName()
        );
        var baseCache = this.activeRequestsCache.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        return tags -> baseCache.computeIfAbsent(tags, t -> activeRequests(publicApiRequest, request, t));
    }

    protected Timer requestDuration(PublicApiRequest publicApiRequest, HttpServerRequest request, Tags additionalTags) {
        var method = publicApiRequest.method();
        var scheme = publicApiRequest.scheme();
        var host = publicApiRequest.hostName();
        var pathTemplate = Objects.requireNonNullElse(request.route(), "UNKNOWN_ROUTE");

        var tags = new ArrayList<Tag>();
        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), method));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), pathTemplate));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), scheme));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), host));
        for (var e : this.config.metrics().tags().entrySet()) {
            tags.add(Tag.of(e.getKey(), e.getValue()));
        }
        for (var tag : additionalTags) {
            tags.add(tag);
        }
        var builder = Timer.builder("http.server.request.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tags(tags);

        return builder.register(this.meterRegistry);
    }

    protected AtomicLong activeRequests(PublicApiRequest publicApiRequest, HttpServerRequest request, Tags additionalTags) {
        var method = publicApiRequest.method();
        var scheme = publicApiRequest.scheme();
        var host = publicApiRequest.hostName();
        var pathTemplate = Objects.requireNonNullElse(request.route(), "UNKNOWN_ROUTE");

        var tags = new ArrayList<Tag>();
        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), method));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), pathTemplate));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), scheme));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), host));
        for (var e : this.config.metrics().tags().entrySet()) {
            tags.add(Tag.of(e.getKey(), e.getValue()));
        }
        for (var tag : additionalTags) {
            tags.add(tag);
        }

        var value = new AtomicLong(0);
        Gauge.builder("http.server.active_requests", value, AtomicLong::get)
            .tags(tags)
            .register(this.meterRegistry);
        return value;
    }

    protected Span createSpan(@Nullable String template, PublicApiRequest routerRequest) {
        if (template == null || !this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var span = this.tracer
            .spanBuilder(routerRequest.method() + " " + template)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, routerRequest.method())
            .setAttribute(UrlAttributes.URL_SCHEME, routerRequest.scheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, routerRequest.hostName())
            .setAttribute(UrlAttributes.URL_PATH, routerRequest.path())
            .setAttribute(HttpAttributes.HTTP_ROUTE, template);
        for (var attribute : config.tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span.startSpan();
    }

}
