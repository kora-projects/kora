package ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnMetrics;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCamundaEngineBpmnMetricsFactory implements CamundaEngineBpmnMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerCamundaEngineBpmnMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public CamundaEngineBpmnMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new MicrometerCamundaEngineBpmnMetrics(this.meterRegistry, config);
        } else {
            return null;
        }
    }
}
