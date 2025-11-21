package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.response.ActivatedJob;

public class NoopZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {
    public static final NoopZeebeWorkerTelemetry INSTANCE = new NoopZeebeWorkerTelemetry();

    @Override
    public ZeebeWorkerObservation observe(ActivatedJob job) {
        return NoopZeebeWorkerObservation.INSTANCE;
    }
}
