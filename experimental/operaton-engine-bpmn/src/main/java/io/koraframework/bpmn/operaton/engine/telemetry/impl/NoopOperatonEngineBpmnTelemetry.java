package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineBpmnTelemetry;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineObservation;

public final class NoopOperatonEngineBpmnTelemetry implements OperatonEngineBpmnTelemetry {

    public static final NoopOperatonEngineBpmnTelemetry INSTANCE = new NoopOperatonEngineBpmnTelemetry();

    @Override
    public OperatonEngineObservation observe(String canonicalName) {
        return NoopOperatonEngineObservation.INSTANCE;
    }
}
