package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;

public final class NoopCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {

    public static final NoopCamundaEngineBpmnTelemetry INSTANCE = new NoopCamundaEngineBpmnTelemetry();

    @Override
    public CamundaEngineObservation observe(String canonicalName) {
        return NoopCamundaEngineObservation.INSTANCE;
    }
}
