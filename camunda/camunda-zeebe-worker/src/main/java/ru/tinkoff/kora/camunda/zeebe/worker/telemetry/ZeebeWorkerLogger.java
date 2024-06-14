package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry.ErrorType;

public interface ZeebeWorkerLogger {

    void logStarted(JobContext context);

    void logComplete(JobContext context);

    void logFailed(JobContext context, ErrorType errorType, Throwable throwable);
}
