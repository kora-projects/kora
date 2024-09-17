package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

public interface CamundaEngineBpmnTelemetryFactory {

    CamundaEngineBpmnTelemetry get(CamundaEngineBpmnConfig.CamundaTelemetryConfig telemetryConfig);
}
