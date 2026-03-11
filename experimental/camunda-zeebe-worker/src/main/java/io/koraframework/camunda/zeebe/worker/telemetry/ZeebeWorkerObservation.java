package io.koraframework.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.koraframework.common.telemetry.Observation;

public interface ZeebeWorkerObservation extends Observation {
    void observeFinalCommandStep(FinalCommandStep<?> command);

    void observeHandle(String type, ActivatedJob job);
}
