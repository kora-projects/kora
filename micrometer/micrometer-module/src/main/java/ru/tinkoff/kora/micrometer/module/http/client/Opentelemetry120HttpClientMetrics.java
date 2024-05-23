package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry120HttpClientMetrics implements HttpClientMetrics {

    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry120HttpClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void record(int statusCode, long processingTimeNanos, String method, String host, String scheme, String target) {
        this.duration.computeIfAbsent(new DurationKey(statusCode, method, host, scheme, target), this::duration)
            .record((double) processingTimeNanos / 1_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var builder = DistributionSummary.builder("http.client.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag("http.method", key.method)
            .tag(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method)
            .tag(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host)
            .tag(SemanticAttributes.URL_SCHEME.getKey(), key.scheme)
            .tag(SemanticAttributes.HTTP_ROUTE.getKey(), key.target)
            .tag(SemanticAttributes.HTTP_TARGET.getKey(), key.target)
            .tag(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .tag("http.status_code", Integer.toString(key.statusCode()));
        return builder.register(meterRegistry);
    }

    private record DurationKey(int statusCode, String method, String host, String scheme, String target) {}
}
