package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface ZeebeWorkerTelemetryFactory {

    ZeebeWorkerTelemetry get(String workerType, TelemetryConfig config);
}
