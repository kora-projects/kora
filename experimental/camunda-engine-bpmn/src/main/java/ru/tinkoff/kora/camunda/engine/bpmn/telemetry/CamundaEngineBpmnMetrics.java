package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineBpmnMetrics {

    void executionStarted(String javaDelegateName, DelegateExecution execution);

    void executionFinished(String javaDelegateName,
                           DelegateExecution execution,
                           long processingTimeNano,
                           @Nullable Throwable exception);
}
