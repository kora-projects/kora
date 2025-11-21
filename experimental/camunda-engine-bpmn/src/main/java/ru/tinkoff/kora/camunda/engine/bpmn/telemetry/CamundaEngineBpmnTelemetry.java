package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

public interface CamundaEngineBpmnTelemetry {

    CamundaEngineObservation observe(String canonicalName);

}
