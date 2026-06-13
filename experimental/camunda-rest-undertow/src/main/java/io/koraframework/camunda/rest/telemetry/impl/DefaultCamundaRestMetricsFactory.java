package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultCamundaRestMetricsFactory {

    public static final DefaultCamundaRestMetricsFactory INSTANCE = new DefaultCamundaRestMetricsFactory();

    public DefaultCamundaRestMetrics create(DefaultCamundaRestTelemetry.TelemetryContext context) {
        return new DefaultCamundaRestMetrics(context);
    }

    public static class DefaultCamundaRestMetrics {

        public record DurationKey(String method,
                                  String pathTemplate,
                                  String scheme,
                                  String host,
                                  int statusCode,
                                  HttpResultCode resultCode,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(method, pathTemplate, scheme, host, statusCode, resultCode, errorType, tags);
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

        protected final ConcurrentHashMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<ActiveRequestsKey, AtomicLong> activeRequestsCache = new ConcurrentHashMap<>();

        protected final DefaultCamundaRestTelemetry.TelemetryContext context;

        public DefaultCamundaRestMetrics(DefaultCamundaRestTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordDuration(HttpServerExchange exchange,
                                   @Nullable String route,
                                   int statusCode,
                                   @Nullable HttpResultCode resultCode,
                                   @Nullable Throwable throwable,
                                   long processingTimeNanos) {
            var key = createDurationKey(exchange, route, statusCode, resultCode, throwable);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(this.context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        public void recordActive(HttpServerExchange exchange, String route, int delta) {
            var key = createActiveRequestsKey(exchange, route);
            var activeRequests = this.activeRequestsCache.computeIfAbsent(key, _ -> createActiveRequests(key));
            activeRequests.addAndGet(delta);
        }

        protected DurationKey createDurationKey(HttpServerExchange exchange,
                                                @Nullable String route,
                                                int statusCode,
                                                @Nullable HttpResultCode resultCode,
                                                @Nullable Throwable throwable) {
            return new DurationKey(
                exchange.getRequestMethod().toString(),
                Objects.requireNonNullElse(route, "UNKNOWN_ROUTE"),
                exchange.getRequestScheme(),
                exchange.getHostAndPort(),
                statusCode,
                Objects.requireNonNullElse(resultCode, HttpResultCode.SERVER_ERROR),
                throwable == null ? null : throwable.getClass(),
                null
            );
        }

        protected ActiveRequestsKey createActiveRequestsKey(HttpServerExchange exchange, String route) {
            return new ActiveRequestsKey(
                exchange.getRequestMethod().toString(),
                route,
                exchange.getRequestScheme(),
                exchange.getHostAndPort(),
                null
            );
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var staticTags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size() + extraTagsSize(metricKey.extraTags()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), metricKey.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(metricKey.statusCode())));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate()));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), metricKey.scheme()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), metricKey.host()));
            staticTags.add(Tag.of("http.response.result_code", metricKey.resultCode().string()));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), metricKey.errorType() == null ? "" : metricKey.errorType().getCanonicalName()));
            addConfiguredTags(staticTags);
            addExtraTags(staticTags, metricKey.extraTags());

            return Timer.builder("http.server.request.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected AtomicLong createActiveRequests(ActiveRequestsKey metricKey) {
            var staticTags = new ArrayList<Tag>(4 + this.context.config().metrics().tags().size() + extraTagsSize(metricKey.extraTags()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), metricKey.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), metricKey.pathTemplate()));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), metricKey.scheme()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), metricKey.host()));
            addConfiguredTags(staticTags);
            addExtraTags(staticTags, metricKey.extraTags());

            var value = new AtomicLong(0);
            Gauge.builder("http.server.active_requests", value, AtomicLong::get)
                .tags(Tags.of(staticTags))
                .register(this.context.meterRegistry());
            return value;
        }

        protected void addConfiguredTags(ArrayList<Tag> staticTags) {
            for (var e : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(e.getKey(), e.getValue()));
            }
        }

        protected void addExtraTags(ArrayList<Tag> staticTags, @Nullable Tags extraTags) {
            if (extraTags != null) {
                for (Tag extraTag : extraTags) {
                    staticTags.add(extraTag);
                }
            }
        }

        protected int extraTagsSize(@Nullable Tags tags) {
            if (tags == null) {
                return 0;
            }
            var size = 0;
            for (Tag _ : tags) {
                size++;
            }
            return size;
        }
    }
}
