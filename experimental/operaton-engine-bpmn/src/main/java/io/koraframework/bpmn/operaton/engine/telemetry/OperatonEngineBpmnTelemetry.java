package io.koraframework.bpmn.operaton.engine.telemetry;

public interface OperatonEngineBpmnTelemetry {

    OperatonEngineObservation observe(String canonicalName);
}
