package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;

public final class NoopCamundaEngineTelemetry implements CamundaEngineTelemetry {

    public static final NoopCamundaEngineTelemetry INSTANCE = new NoopCamundaEngineTelemetry();

    @Override
    public CamundaEngineObservation observe(String canonicalName) {
        return NoopCamundaEngineObservation.INSTANCE;
    }
}
