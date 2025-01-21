package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;

public interface ZeebeWorkerTelemetry {

    enum ErrorType {
        USER,
        SYSTEM
    }

    interface ZeebeWorkerTelemetryContext {

        void close();

        void close(ErrorType errorType, Throwable throwable);
    }

    ZeebeWorkerTelemetryContext get(JobContext jobContext);
}
