package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineBpmnTracer {

    interface CamundaEngineSpan {

        void close(@Nullable Throwable exception);
    }

    CamundaEngineSpan createSpan(String javaDelegateName, DelegateExecution execution);
}
