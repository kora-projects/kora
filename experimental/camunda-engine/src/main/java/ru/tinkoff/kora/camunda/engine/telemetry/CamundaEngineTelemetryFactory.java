package ru.tinkoff.kora.camunda.engine.telemetry;

import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;

public interface CamundaEngineTelemetryFactory {

    CamundaEngineTelemetry get(CamundaEngineConfig.CamundaTelemetryConfig telemetryConfig);
}
