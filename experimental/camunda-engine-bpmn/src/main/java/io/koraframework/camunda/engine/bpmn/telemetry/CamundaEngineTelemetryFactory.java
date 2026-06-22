package io.koraframework.camunda.engine.bpmn.telemetry;

public interface CamundaEngineTelemetryFactory {

    CamundaEngineTelemetry get(CamundaEngineTelemetryConfig telemetryConfig);
}
