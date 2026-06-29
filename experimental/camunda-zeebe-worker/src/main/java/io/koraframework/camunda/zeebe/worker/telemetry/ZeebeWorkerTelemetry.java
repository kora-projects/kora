package io.koraframework.camunda.zeebe.worker.telemetry;

import io.camunda.client.api.response.ActivatedJob;

public interface ZeebeWorkerTelemetry {

    ZeebeWorkerObservation observe(ActivatedJob job);
}
