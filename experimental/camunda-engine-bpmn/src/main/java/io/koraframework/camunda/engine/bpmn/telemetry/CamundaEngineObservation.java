package io.koraframework.camunda.engine.bpmn.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineObservation extends Observation {
    void observeExecution(DelegateExecution execution);
}
