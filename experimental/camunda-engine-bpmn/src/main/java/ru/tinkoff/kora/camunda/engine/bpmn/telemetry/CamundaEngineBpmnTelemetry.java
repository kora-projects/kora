package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineBpmnTelemetry {

    interface CamundaEngineTelemetryContext {

        default void close() {
            close(null);
        }

        void close(@Nullable Throwable exception);
    }

    CamundaEngineTelemetryContext get(String javaDelegateName, DelegateExecution execution);
}
