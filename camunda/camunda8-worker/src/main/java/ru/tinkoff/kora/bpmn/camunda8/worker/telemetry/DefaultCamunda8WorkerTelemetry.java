package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;

public final class DefaultCamunda8WorkerTelemetry implements Camunda8WorkerTelemetry {

    private final String workerType;
    @Nullable
    private final Camunda8WorkerLogger logger;
    @Nullable
    private final Camunda8WorkerMetrics metrics;

    public DefaultCamunda8WorkerTelemetry(String workerType,
                                          @Nullable Camunda8WorkerLogger logger,
                                          @Nullable Camunda8WorkerMetrics metrics) {
        this.workerType = workerType;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public Camunda8WorkerTelemetryContext get(JobContext jobContext) {
        var startTime = System.nanoTime();
        if (logger != null) {
            logger.logStarted(jobContext);
        }

        return new DefaultCamunda8WorkerTelemetryContext(startTime, jobContext, logger, metrics);
    }

    private record DefaultCamunda8WorkerTelemetryContext(long startedInNanos,
                                                         JobContext jobContext,
                                                         @Nullable Camunda8WorkerLogger logger,
                                                         @Nullable Camunda8WorkerMetrics metrics) implements Camunda8WorkerTelemetryContext {

        @Override
        public void close() {
            if (logger != null) {
                logger.logComplete(jobContext);
            }
            if (metrics != null) {
                metrics.recordComplete(jobContext, startedInNanos);
            }
        }

        @Override
        public void close(ErrorType errorType, Throwable throwable) {
            if (logger != null) {
                logger.logFailed(jobContext, errorType, throwable);
            }
            if (metrics != null) {
                metrics.recordFailed(jobContext, startedInNanos, errorType, throwable);
            }
        }
    }
}
