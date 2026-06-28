package io.koraframework.bpmn.operaton.engine.telemetry;

public interface OperatonEngineTelemetryFactory {

    OperatonEngineTelemetry get(OperatonEngineTelemetryConfig telemetryConfig);
}
