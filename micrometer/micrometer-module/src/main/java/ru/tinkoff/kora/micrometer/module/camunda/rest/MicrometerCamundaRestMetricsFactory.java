package ru.tinkoff.kora.micrometer.module.camunda.rest;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetrics;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCamundaRestMetricsFactory implements CamundaRestMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerCamundaRestMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public CamundaRestMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Micrometer120CamundaRestMetrics(this.meterRegistry, config);
                case V123 -> new Micrometer123CamundaRestMetrics(this.meterRegistry, config);
            };
        } else {
            return null;
        }
    }
}
