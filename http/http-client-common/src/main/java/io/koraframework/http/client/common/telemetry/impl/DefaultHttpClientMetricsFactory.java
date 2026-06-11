package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientMetricsFactory {

    public static final DefaultHttpClientMetricsFactory INSTANCE = new DefaultHttpClientMetricsFactory();

    public DefaultHttpClientMetrics create(DefaultHttpClientTelemetry.TelemetryContext context) {
        return new DefaultHttpClientMetrics(context);
    }

    public static class DefaultHttpClientMetrics {

        public record DurationKey(int statusCode,
                                  String method,
                                  String host,
                                  String scheme,
                                  String target,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(statusCode, method, host, scheme, target, errorType, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();

        protected final DefaultHttpClientTelemetry.TelemetryContext context;

        public DefaultHttpClientMetrics(DefaultHttpClientTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordFailure(HttpClientRequest rq, Throwable exception, long processingTimeNanos) {
            var key = createMetricClientDurationKey(rq, null, exception);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(key, rq, null, exception).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        public void recordSuccess(HttpClientRequest rq, HttpClientResponse rs, long processingTimeNanos) {
            var key = createMetricClientDurationKey(rq, rs, null);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(key, rq, rs, null).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricClientDurationKey(HttpClientRequest rq,
                                                            @Nullable HttpClientResponse rs,
                                                            @Nullable Throwable exception) {
            var code = (rs == null) ? -1 : rs.code();
            if (exception instanceof CompletionException ce && ce.getCause() != null) {
                exception = ce.getCause();
            }
            var errorType = (exception == null) ? null : exception.getClass();
            return new DurationKey(code, rq.method(), rq.uri().getHost(), rq.uri().getScheme(), rq.uriTemplate(), errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricClientDuration(DurationKey metricKey,
                                                           HttpClientRequest request,
                                                           @Nullable HttpClientResponse rs,
                                                           @Nullable Throwable throwable) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(9 + this.context.config().metrics().tags().size() + extraTags);

            var statusCodeStr = Integer.toString(metricKey.statusCode);
            var errorType = (throwable == null) ? "" : throwable.getClass().getCanonicalName();

            staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), statusCodeStr));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.uri().getHost()));
            staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.uri().getScheme()));
            staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), request.uriTemplate()));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));
            staticTags.add(Tag.of(DefaultHttpClientTelemetry.SYSTEM_CONFIG_PATH, this.context.clientConfigPath()));
            staticTags.add(Tag.of(DefaultHttpClientTelemetry.SYSTEM_NAME_SIMPLE, this.context.clientSimpleName()));
            staticTags.add(Tag.of(DefaultHttpClientTelemetry.SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName()));

            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("http.client.request.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }
    }
}
