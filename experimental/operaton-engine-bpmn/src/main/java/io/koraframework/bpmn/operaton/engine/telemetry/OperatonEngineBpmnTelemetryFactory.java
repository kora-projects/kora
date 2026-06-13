package io.koraframework.bpmn.operaton.engine.telemetry;

import io.koraframework.bpmn.operaton.engine.OperatonEngineBpmnConfig;

public interface OperatonEngineBpmnTelemetryFactory {

    OperatonEngineBpmnTelemetry get(OperatonEngineBpmnConfig.OperatonTelemetryConfig telemetryConfig);
}
