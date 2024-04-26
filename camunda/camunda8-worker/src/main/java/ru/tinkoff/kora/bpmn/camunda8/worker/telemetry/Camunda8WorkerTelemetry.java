package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;

public interface Camunda8WorkerTelemetry {

    enum ErrorType {
        USER,
        SYSTEM
    }

    interface Camunda8WorkerTelemetryContext {

        void close();

        void close(ErrorType errorType, Throwable throwable);
    }

    Camunda8WorkerTelemetryContext get(JobContext jobContext);
}
