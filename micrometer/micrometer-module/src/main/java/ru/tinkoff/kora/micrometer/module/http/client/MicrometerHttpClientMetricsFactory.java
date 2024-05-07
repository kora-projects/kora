package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerHttpClientMetricsFactory implements HttpClientMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerHttpClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Override
    @Nullable
    public HttpClientMetrics get(TelemetryConfig.MetricsConfig metrics, String clientName) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120HttpClientMetrics(meterRegistry, metrics);
                case V123 -> new Opentelemetry123HttpClientMetrics(meterRegistry, metrics);
            };
        } else {
            return null;
        }
    }
}
