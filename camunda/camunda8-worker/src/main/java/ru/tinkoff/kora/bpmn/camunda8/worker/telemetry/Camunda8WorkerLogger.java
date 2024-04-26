package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry.ErrorType;

public interface Camunda8WorkerLogger {

    void logStarted(JobContext context);

    void logComplete(JobContext context);

    void logFailed(JobContext context, ErrorType errorType, Throwable throwable);
}
