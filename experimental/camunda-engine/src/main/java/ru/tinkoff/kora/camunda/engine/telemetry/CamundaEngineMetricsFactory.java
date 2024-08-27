package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaEngineMetricsFactory {

    @Nullable
    CamundaEngineMetrics get(TelemetryConfig.MetricsConfig config);
}
