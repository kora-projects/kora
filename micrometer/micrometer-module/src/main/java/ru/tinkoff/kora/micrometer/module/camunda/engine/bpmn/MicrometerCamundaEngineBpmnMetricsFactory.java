package ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnMetrics;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCamundaEngineBpmnMetricsFactory implements CamundaEngineBpmnMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerCamundaEngineBpmnMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public CamundaEngineBpmnMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Micrometer120CamundaEngineBpmnMetrics(this.meterRegistry, config);
                case V123 -> new Micrometer123CamundaEngineBpmnMetrics(this.meterRegistry, config);
            };
        } else {
            return null;
        }
    }
}
