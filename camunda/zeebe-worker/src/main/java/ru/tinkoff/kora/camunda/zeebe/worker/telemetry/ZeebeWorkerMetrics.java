package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry.ErrorType;

public interface ZeebeWorkerMetrics {

    void recordComplete(JobContext jobContext, long startTimeInNanos);

    void recordFailed(JobContext jobContext, long startTimeInNanos, ErrorType errorType, Throwable throwable);
}
