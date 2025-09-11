package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.client.tag.MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerHttpClientMetricsFactory implements HttpClientMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MicrometerHttpClientTagsProvider tagsProvider;

    public MicrometerHttpClientMetricsFactory(MeterRegistry meterRegistry,
                                              MicrometerHttpClientTagsProvider tagsProvider) {
        this.meterRegistry = meterRegistry;
        this.tagsProvider = tagsProvider;
    }

    @Nullable
    @Override
    public HttpClientMetrics get(TelemetryConfig.MetricsConfig metrics, String clientName) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return new OpentelemetryHttpClientMetrics(meterRegistry, metrics, tagsProvider);
        } else {
            return null;
        }
    }
}
