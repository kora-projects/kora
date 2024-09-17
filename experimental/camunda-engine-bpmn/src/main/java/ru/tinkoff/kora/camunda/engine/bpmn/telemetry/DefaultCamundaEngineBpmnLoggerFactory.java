package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

import java.util.Objects;

public final class DefaultCamundaEngineBpmnLoggerFactory implements CamundaEngineBpmnLoggerFactory {

    @Nullable
    @Override
    public CamundaEngineBpmnLogger get(CamundaEngineBpmnConfig.CamundaEngineLogConfig logging) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultCamundaEngineBpmnLogger(logging.stacktrace());
        } else {
            return null;
        }
    }
}
