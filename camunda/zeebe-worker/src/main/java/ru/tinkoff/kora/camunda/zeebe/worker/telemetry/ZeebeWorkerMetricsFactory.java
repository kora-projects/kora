package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface ZeebeWorkerMetricsFactory {

    @Nullable
    ZeebeWorkerMetrics get(TelemetryConfig.MetricsConfig config);
}
