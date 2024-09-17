package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;


import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineBpmnLogger {

    void logStart(String javaDelegateName,
                  DelegateExecution execution);

    void logEnd(String javaDelegateName,
                DelegateExecution execution,
                long processingTimeInNanos,
                @Nullable Throwable exception);
}
