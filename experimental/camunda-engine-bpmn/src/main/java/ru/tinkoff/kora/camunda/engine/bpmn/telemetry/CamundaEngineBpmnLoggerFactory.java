package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

public interface CamundaEngineBpmnLoggerFactory {

    @Nullable
    CamundaEngineBpmnLogger get(CamundaEngineBpmnConfig.CamundaEngineLogConfig logging);
}
