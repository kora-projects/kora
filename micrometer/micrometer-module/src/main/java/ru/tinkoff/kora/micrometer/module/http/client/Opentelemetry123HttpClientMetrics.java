package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry123HttpClientMetrics implements HttpClientMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry123HttpClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void record(int statusCode, HttpResultCode resultCode, String scheme, String host, String method, String pathTemplate, long processingTimeNanos, Throwable throwable) {
        this.duration.computeIfAbsent(new DurationKey(statusCode, method, host, scheme, pathTemplate), this::duration)
            .record((double) processingTimeNanos / 1_000_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var builder = DistributionSummary.builder("http.client.request.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method)
            .tag(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .tag(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host)
            .tag(SemanticAttributes.URL_SCHEME.getKey(), key.scheme)
            .tag(SemanticAttributes.HTTP_ROUTE.getKey(), key.target)
            .tag("http.status_code", Integer.toString(key.statusCode()));
        return builder.register(meterRegistry);
    }

    private record DurationKey(int statusCode, String method, String host, String scheme, String target) {}
}
