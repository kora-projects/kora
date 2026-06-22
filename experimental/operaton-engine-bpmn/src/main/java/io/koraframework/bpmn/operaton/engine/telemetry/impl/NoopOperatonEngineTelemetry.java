package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetry;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineObservation;

public final class NoopOperatonEngineTelemetry implements OperatonEngineTelemetry {

    public static final NoopOperatonEngineTelemetry INSTANCE = new NoopOperatonEngineTelemetry();

    @Override
    public OperatonEngineObservation observe(String canonicalName) {
        return NoopOperatonEngineObservation.INSTANCE;
    }
}
