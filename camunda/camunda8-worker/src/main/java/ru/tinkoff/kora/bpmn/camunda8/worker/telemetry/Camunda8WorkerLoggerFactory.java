package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface Camunda8WorkerLoggerFactory {

    @Nullable
    Camunda8WorkerLogger get(TelemetryConfig.LogConfig config);
}
