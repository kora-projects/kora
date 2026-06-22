package io.koraframework.camunda.engine.bpmn.telemetry;

public interface CamundaEngineTelemetry {

    CamundaEngineObservation observe(String canonicalName);

}
