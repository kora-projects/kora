package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerObservation;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;

public final class NoopZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

    public static final NoopZeebeWorkerTelemetry INSTANCE = new NoopZeebeWorkerTelemetry();

    private NoopZeebeWorkerTelemetry() {}

    @Override
    public ZeebeWorkerObservation observe(ActivatedJob job) {
        return NoopZeebeWorkerObservation.INSTANCE;
    }
}
