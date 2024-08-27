package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineTelemetry {

    interface CamundaEngineTelemetryContext {

        default void close() {
            close(null);
        }

        void close(@Nullable Throwable exception);
    }

    CamundaEngineTelemetryContext get(String javaDelegateName, DelegateExecution execution);
}
