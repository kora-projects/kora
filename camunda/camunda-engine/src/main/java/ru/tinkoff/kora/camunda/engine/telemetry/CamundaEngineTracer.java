package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public interface CamundaEngineTracer {

    interface CamundaEngineSpan {

        void close(@Nullable Throwable exception);
    }

    CamundaEngineSpan createSpan(String javaDelegateName, DelegateExecution execution);
}
