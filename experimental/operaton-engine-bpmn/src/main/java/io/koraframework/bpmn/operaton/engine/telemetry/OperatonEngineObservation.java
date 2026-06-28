package io.koraframework.bpmn.operaton.engine.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.operaton.bpm.engine.delegate.DelegateExecution;

public interface OperatonEngineObservation extends Observation {

    void observeExecution(DelegateExecution execution);
}
