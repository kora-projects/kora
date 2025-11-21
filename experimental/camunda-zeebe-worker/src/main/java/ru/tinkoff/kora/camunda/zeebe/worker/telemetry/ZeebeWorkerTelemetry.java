package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.response.ActivatedJob;

public interface ZeebeWorkerTelemetry {

    ZeebeWorkerObservation observe(ActivatedJob job);
}
