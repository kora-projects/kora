package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;

public interface ZeebeWorkerTracer {

    interface ZeebeWorkerSpan {

        default void close() {
            close(null, null);
        }

        void close(@Nullable ZeebeWorkerTelemetry.ErrorType errorType, @Nullable Throwable exception);
    }

    ZeebeWorkerSpan createSpan(String workerType, JobContext jobContext);
}
