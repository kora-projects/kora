package ru.tinkoff.kora.micrometer.module.http.server;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerHttpServerMetricsFactory implements HttpServerMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final MicrometerHttpServerTagsProvider httpServerTagsProvider;
    private final MetricsConfig metricsConfig;

    public MicrometerHttpServerMetricsFactory(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.httpServerTagsProvider = httpServerTagsProvider;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public HttpServerMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120HttpServerMetrics(this.meterRegistry, this.httpServerTagsProvider, config);
                case V123 -> new Opentelemetry123HttpServerMetrics(this.meterRegistry, this.httpServerTagsProvider, config);
            };
        } else {
            return null;
        }
    }
}
