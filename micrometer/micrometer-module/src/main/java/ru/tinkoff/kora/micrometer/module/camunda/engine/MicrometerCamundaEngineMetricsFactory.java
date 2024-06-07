package ru.tinkoff.kora.micrometer.module.camunda.engine;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.telemetry.CamundaEngineMetrics;
import ru.tinkoff.kora.camunda.engine.telemetry.CamundaEngineMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCamundaEngineMetricsFactory implements CamundaEngineMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerCamundaEngineMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public CamundaEngineMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Micrometer120CamundaEngineMetrics(this.meterRegistry, config);
                case V123 -> new Micrometer123CamundaEngineMetrics(this.meterRegistry, config);
            };
        } else {
            return null;
        }
    }
}
