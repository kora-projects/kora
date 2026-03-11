package io.koraframework.camunda.engine.bpmn.telemetry;

public interface CamundaEngineBpmnTelemetry {

    CamundaEngineObservation observe(String canonicalName);

}
