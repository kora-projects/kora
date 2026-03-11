package io.koraframework.camunda.zeebe.worker.telemetry;

import io.koraframework.telemetry.common.TelemetryConfig;

public interface ZeebeWorkerTelemetryFactory {

    ZeebeWorkerTelemetry get(String workerType, TelemetryConfig config);
}
