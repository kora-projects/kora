package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientMetrics {

    protected static final String SYSTEM_CONFIG = "system.name";
    protected static final String SYSTEM_IMPL = "system.impl";

    private record DurationKey(int statusCode, String method, String host, String scheme, String target, @Nullable Class<? extends Throwable> errorType) {}

    private final ConcurrentHashMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();

    protected final String clientName;
    protected final String clientImpl;
    protected final String clientSimpleImpl;
    protected final MeterRegistry meterRegistry;
    protected final HttpClientTelemetryConfig.HttpClientMetricsConfig config;

    public DefaultHttpClientMetrics(String clientName,
                                    String clientImpl,
                                    MeterRegistry meterRegistry,
                                    HttpClientTelemetryConfig.HttpClientMetricsConfig config) {
        this.clientName = clientName;
        this.clientImpl = clientImpl;
        this.clientSimpleImpl = clientImpl.substring(clientImpl.lastIndexOf('.') + 1);
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    public void recordFailure(HttpClientRequest rq, Throwable exception, long processingTimeNanos) {
        if (this.config.enabled()) {
            var code = -1;
            var errorType = exception.getClass();
            var key = new DurationKey(code, rq.method(), rq.uri().getHost(), rq.uri().getScheme(), rq.uriTemplate(), errorType);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(rq, -1, exception).register(meterRegistry));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }
    }

    public void recordSuccess(HttpClientRequest rq, HttpClientResponse rs, long processingTimeNanos) {
        if (this.config.enabled()) {
            int code = rs.code();
            var key = new DurationKey(code, rq.method(), rq.uri().getHost(), rq.uri().getScheme(), rq.uriTemplate(), null);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(rq, code, null).register(meterRegistry));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }
    }

    protected Timer.Builder createMetricClientDuration(HttpClientRequest request, int statusCode, @Nullable Throwable throwable) {
        var staticTags = new ArrayList<Tag>(9 + this.config.tags().size());

        var statusCodeStr = Integer.toString(statusCode);
        var errorType = (throwable == null) ? "" : throwable.getClass().getCanonicalName();

        staticTags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), request.method()));
        staticTags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), statusCodeStr));
        staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), request.uri().getHost()));
        staticTags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), request.uri().getScheme()));
        staticTags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), request.uriTemplate()));
        staticTags.add(Tag.of("http.status_code", statusCodeStr));
        staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));
        staticTags.add(Tag.of(SYSTEM_IMPL, clientSimpleImpl));
        staticTags.add(Tag.of(SYSTEM_CONFIG, clientName));

        for (var tag : config.tags().entrySet()) {
            staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
        }

        return Timer.builder("http.client.request.duration")
            .serviceLevelObjectives(this.config.slo())
            .tags(Tags.of(staticTags));
    }
}
