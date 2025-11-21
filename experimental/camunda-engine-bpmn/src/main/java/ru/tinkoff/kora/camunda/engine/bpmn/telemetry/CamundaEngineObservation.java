package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.common.telemetry.Observation;

public interface CamundaEngineObservation extends Observation {
    void observeExecution(DelegateExecution execution);
}
