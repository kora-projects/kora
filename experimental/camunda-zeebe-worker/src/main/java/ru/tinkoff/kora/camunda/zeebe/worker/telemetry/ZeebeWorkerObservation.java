package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface ZeebeWorkerObservation extends Observation {
    void observeFinalCommandStep(FinalCommandStep<?> command);

    void observeHandle(String type, ActivatedJob job);
}
