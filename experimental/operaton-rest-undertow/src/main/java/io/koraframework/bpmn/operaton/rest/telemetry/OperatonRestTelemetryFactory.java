package io.koraframework.bpmn.operaton.rest.telemetry;

public interface OperatonRestTelemetryFactory {

    OperatonRestTelemetry get(OperatonRestTelemetryConfig telemetryConfig);
}
