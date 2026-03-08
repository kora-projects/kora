package io.koraframework.camunda.engine.bpmn.telemetry;

import io.koraframework.camunda.engine.bpmn.CamundaEngineBpmnConfig;

public interface CamundaEngineBpmnTelemetryFactory {

    CamundaEngineBpmnTelemetry get(CamundaEngineBpmnConfig.CamundaTelemetryConfig telemetryConfig);
}
