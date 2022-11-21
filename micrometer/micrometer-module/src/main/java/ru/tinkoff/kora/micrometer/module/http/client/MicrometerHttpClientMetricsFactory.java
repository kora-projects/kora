package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetricsFactory;

public final class MicrometerHttpClientMetricsFactory implements HttpClientMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerHttpClientMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public HttpClientMetrics get(String clientName) {
        return new MicrometerHttpClientMetrics(this.meterRegistry);
    }
}
