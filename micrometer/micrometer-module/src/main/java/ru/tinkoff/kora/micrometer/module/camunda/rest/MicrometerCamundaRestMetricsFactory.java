package ru.tinkoff.kora.micrometer.module.camunda.rest;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetrics;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCamundaRestMetricsFactory implements CamundaRestMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerCamundaRestMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public CamundaRestMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new MicrometerCamundaRestMetrics(this.meterRegistry, config);
        } else {
            return null;
        }
    }
}
