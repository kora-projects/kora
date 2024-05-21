package ru.tinkoff.kora.camunda.engine.telemetry;


import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineLogger {

    void logStart(String javaDelegateName, DelegateExecution execution);

    void logEnd(String javaDelegateName,
                DelegateExecution execution,
                long processingTimeInNanos,
                @Nullable Throwable exception);
}
