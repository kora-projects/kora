package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

public class NoopCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {
    public static final NoopCamundaEngineBpmnTelemetry INSTANCE = new NoopCamundaEngineBpmnTelemetry();

    @Override
    public CamundaEngineObservation observe(String canonicalName) {
        return NoopCamundaEngineObservation.INSTANCE;
    }
}
