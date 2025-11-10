package ru.tinkoff.kora.http.client.common.telemetry.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientMetrics {
    private final ConcurrentHashMap<DurationKey, Timer> duration = new ConcurrentHashMap<>();

    protected final MeterRegistry meterRegistry;
    protected final HttpClientTelemetryConfig.HttpClientMetricsConfig config;

    private record DurationKey(int statusCode, String method, String host, String scheme, String target, @Nullable Class<? extends Throwable> errorType) {}

    public DefaultHttpClientMetrics(MeterRegistry meterRegistry, HttpClientTelemetryConfig.HttpClientMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    public void recordFailure(HttpClientRequest rq, Throwable exception, long processingTimeNanos) {
        if (this.config.enabled()) {
            var code = -1;
            var errorType = exception.getClass();
            var key = new DurationKey(code, rq.method(), rq.uri().getHost(), rq.uri().getScheme(), rq.uriTemplate(), errorType);
            this.duration.computeIfAbsent(key, _ -> clientDuration(rq, -1, errorType).register(meterRegistry))
                .record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }
    }

    public void recordSuccess(HttpClientRequest rq, HttpClientResponse rs, long processingTimeNanos) {
        if (this.config.enabled()) {
            int code = rs.code();
            var key = new DurationKey(code, rq.method(), rq.uri().getHost(), rq.uri().getScheme(), rq.uriTemplate(), null);
            this.duration.computeIfAbsent(key, _ -> clientDuration(rq, code, null).register(meterRegistry))
                .record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }
    }

    protected Timer.Builder clientDuration(HttpClientRequest rq, int statusCode, Class<? extends Throwable> errorType) {
        var builder = Timer.builder("http.client.request.duration")
            .serviceLevelObjectives(this.config.slo());
        var statusCodeStr = Integer.toString(statusCode);
        var tags = new ArrayList<Tag>(7);
        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), rq.method()));
        tags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), statusCodeStr));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), rq.uri().getHost()));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), rq.uri().getScheme()));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), rq.uriTemplate()));
        tags.add(Tag.of("http.status_code", statusCodeStr));
        if (errorType != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType.getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }
        builder.tags(tags);

        for (var tag : config.tags().entrySet()) {
            builder.tag(tag.getKey(), tag.getValue());
        }

        return builder;
    }
}
