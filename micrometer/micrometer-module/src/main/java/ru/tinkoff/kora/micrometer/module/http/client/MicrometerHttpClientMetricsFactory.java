package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;

public final class MicrometerHttpClientMetricsFactory implements HttpClientMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final MetricsConfig.HttpClientMetricsConfig config;

    public MicrometerHttpClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig.HttpClientMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public HttpClientMetrics get(String clientName) {
        return new MicrometerHttpClientMetrics(this.meterRegistry, this.config);
    }
}
