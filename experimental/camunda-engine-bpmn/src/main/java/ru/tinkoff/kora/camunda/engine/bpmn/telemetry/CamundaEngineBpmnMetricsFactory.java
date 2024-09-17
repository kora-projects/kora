package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaEngineBpmnMetricsFactory {

    @Nullable
    CamundaEngineBpmnMetrics get(TelemetryConfig.MetricsConfig config);
}
