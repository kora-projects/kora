package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.micrometer.module.http.client.tag.DurationKey;
import ru.tinkoff.kora.micrometer.module.http.client.tag.MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry120HttpClientMetrics implements HttpClientMetrics {

    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final MicrometerHttpClientTagsProvider tagsProvider;

    public Opentelemetry120HttpClientMetrics(MeterRegistry meterRegistry,
                                             TelemetryConfig.MetricsConfig config,
                                             MicrometerHttpClientTagsProvider tagsProvider) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.tagsProvider = tagsProvider;
    }

    @Override
    public void record(@Nullable Integer statusCode,
                       HttpResultCode resultCode,
                       String scheme,
                       String host,
                       String method,
                       String pathTemplate,
                       HttpHeaders headers,
                       long processingTimeNanos,
                       @Nullable Throwable exception) {
        int code = statusCode == null ? -1 : statusCode;
        var errorType = exception != null ? exception.getClass() : null;
        var key = new DurationKey(code, method, host, scheme, pathTemplate, errorType);
        this.duration.computeIfAbsent(key, k -> buildMetrics(k, resultCode, headers))
            .record((double) processingTimeNanos / 1_000_000);
    }

    private DistributionSummary buildMetrics(DurationKey key, HttpResultCode resultCode, HttpHeaders headers) {
        var builder = DistributionSummary.builder("http.client.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(tagsProvider.getDurationTags(key, resultCode, headers));

        return builder.register(meterRegistry);
    }
}
