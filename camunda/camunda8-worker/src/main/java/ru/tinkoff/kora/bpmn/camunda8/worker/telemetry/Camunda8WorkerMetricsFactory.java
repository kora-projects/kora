package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry.ErrorType;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface Camunda8WorkerMetricsFactory {

    @Nullable
    Camunda8WorkerMetrics get(TelemetryConfig.MetricsConfig config);
}
