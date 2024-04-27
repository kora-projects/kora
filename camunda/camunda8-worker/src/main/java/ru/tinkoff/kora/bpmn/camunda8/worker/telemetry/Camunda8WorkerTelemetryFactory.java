package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface Camunda8WorkerTelemetryFactory {

    Camunda8WorkerTelemetry get(String workerType, TelemetryConfig config);
}
