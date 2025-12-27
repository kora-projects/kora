package ru.tinkoff.kora.camunda.rest.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.common.telemetry.impl.DefaultHttpServerLogger;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class DefaultCamundaRestTelemetry implements CamundaRestTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));

    private final HttpServerTelemetryConfig config;
    private final Tracer tracer;
    private final DefaultHttpServerLogger logger;
    private final MeterRegistry meterRegistry;

    public DefaultCamundaRestTelemetry(HttpServerTelemetryConfig config, Tracer tracer, DefaultHttpServerLogger logger, MeterRegistry meterRegistry) {
        this.config = config;
        this.tracer = tracer;
        this.logger = logger;
        this.meterRegistry = meterRegistry;
    }

    private record DurationCacheKey(String method, String pathTemplate, String scheme, String host) {}

    private final ConcurrentHashMap<DurationCacheKey, ConcurrentHashMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationCacheKey, ConcurrentHashMap<Tags, AtomicLong>> activeRequestsCache = new ConcurrentHashMap<>();


    @Override
    public CamundaRestObservation observe(HttpServerExchange exchange, @Nullable String route) {
        var span = this.createSpan(route, exchange);
        var requestDuration = this.requestDuration(exchange, route);
        var activeRequests = this.activeRequests(exchange, route);

        return new DefaultCamundaRestObservation(exchange, this.config, exchange.getRequestStartTime(), span, logger, requestDuration, activeRequests);
    }

    protected Meter.MeterProvider<Timer> requestDuration(HttpServerExchange exchange, @Nullable String route) {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        var key = new DurationCacheKey(
            exchange.getRequestMethod().toString(),
            Objects.requireNonNullElse(route, "UNKNOWN_ROUTE"),
            exchange.getRequestScheme(),
            exchange.getHostAndPort()
        );
        var baseCache = this.durationCache.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        return tags -> baseCache.computeIfAbsent(Tags.of(tags), t -> requestDuration(exchange, route, t));
    }

    protected Function<Tags, AtomicLong> activeRequests(HttpServerExchange exchange, @Nullable String route) {
        if (!this.config.metrics().enabled()) {
            return _ -> new AtomicLong(0);
        }
        var key = new DurationCacheKey(
            exchange.getRequestMethod().toString(),
            Objects.requireNonNullElse(route, "UNKNOWN_ROUTE"),
            exchange.getRequestScheme(),
            exchange.getHostAndPort()
        );
        var baseCache = this.activeRequestsCache.computeIfAbsent(key, _ -> new ConcurrentHashMap<>());
        return tags -> baseCache.computeIfAbsent(tags, t -> activeRequests(exchange, route, t));
    }

    protected Timer requestDuration(HttpServerExchange exchange, @Nullable String route, Tags additionalTags) {
        var method = exchange.getRequestMethod().toString();
        var scheme = exchange.getRequestScheme();
        var host = exchange.getHostAndPort();
        var pathTemplate = Objects.requireNonNullElse(route, "UNKNOWN_ROUTE");

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

    protected AtomicLong activeRequests(HttpServerExchange exchange, @Nullable String route, Tags additionalTags) {
        var method = exchange.getRequestMethod().toString();
        var scheme = exchange.getRequestScheme();
        var host = exchange.getHostAndPort();
        var pathTemplate = Objects.requireNonNullElse(route, "UNKNOWN_ROUTE");

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

    protected Span createSpan(String template, HttpServerExchange exchange) {
        if (template == null || !this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var span = this.tracer
            .spanBuilder(exchange.getRequestMethod() + " " + template)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, exchange.getRequestMethod().toString())
            .setAttribute(UrlAttributes.URL_SCHEME, exchange.getRequestScheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, exchange.getHostAndPort())
            .setAttribute(UrlAttributes.URL_PATH, exchange.getRequestPath())
            .setAttribute(HttpAttributes.HTTP_ROUTE, template);
        for (var attribute : config.tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span.startSpan();
    }

}
