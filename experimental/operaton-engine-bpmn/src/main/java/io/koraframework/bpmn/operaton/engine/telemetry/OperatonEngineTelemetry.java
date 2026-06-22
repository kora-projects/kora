package io.koraframework.bpmn.operaton.engine.telemetry;

public interface OperatonEngineTelemetry {

    OperatonEngineObservation observe(String canonicalName);
}
