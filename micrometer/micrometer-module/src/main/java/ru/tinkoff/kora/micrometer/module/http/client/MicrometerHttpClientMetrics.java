package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerHttpClientMetrics implements HttpClientMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final MetricsConfig.HttpClientMetricsConfig config;

    public MicrometerHttpClientMetrics(MeterRegistry meterRegistry, @Nullable MetricsConfig.HttpClientMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void record(int statusCode, long processingTimeNanos, String method, String host, String scheme, String target) {
        this.duration.computeIfAbsent(new DurationKey(statusCode, method, host, scheme, target), this::duration)
            .record((double) processingTimeNanos / 1_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var builder = DistributionSummary.builder("http.client.duration");
        if (this.config != null && this.config.slo() != null) {
            builder.serviceLevelObjectives(this.config.slo().stream().mapToDouble(Double::doubleValue).toArray());
        } else {
            builder.serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000);
        }

        return builder.baseUnit("milliseconds")
            .tag("http.method", key.method)
            .tag("http.host", key.host)
            .tag("http.scheme", key.scheme)
            .tag("http.target", key.target)
            .tag("http.status_code", Integer.toString(key.statusCode()))
            .register(meterRegistry);
    }

    private record DurationKey(int statusCode, String method, String host, String scheme, String target) {}
}
