package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry.ErrorType;

public interface Camunda8WorkerMetrics {

    void recordComplete(JobContext jobContext, long startTimeInNanos);

    void recordFailed(JobContext jobContext, long startTimeInNanos, ErrorType errorType, Throwable throwable);
}
