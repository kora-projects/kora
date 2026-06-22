package io.koraframework.camunda.zeebe.worker.telemetry;

public interface ZeebeWorkerTelemetryFactory {

    ZeebeWorkerTelemetry get(ZeebeWorkerTelemetryConfig config, String workerType);
}
