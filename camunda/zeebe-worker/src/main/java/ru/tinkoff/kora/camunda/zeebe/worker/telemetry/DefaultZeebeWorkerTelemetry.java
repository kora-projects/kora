package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;

public final class DefaultZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

    private final String workerType;
    @Nullable
    private final ZeebeWorkerLogger logger;
    @Nullable
    private final ZeebeWorkerMetrics metrics;

    public DefaultZeebeWorkerTelemetry(String workerType,
                                       @Nullable ZeebeWorkerLogger logger,
                                       @Nullable ZeebeWorkerMetrics metrics) {
        this.workerType = workerType;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public ZeebeWorkerTelemetryContext get(JobContext jobContext) {
        var startTime = System.nanoTime();
        if (logger != null) {
            logger.logStarted(jobContext);
        }

        return new DefaultZeebeWorkerTelemetryContext(startTime, jobContext, logger, metrics);
    }

    private record DefaultZeebeWorkerTelemetryContext(long startedInNanos,
                                                      JobContext jobContext,
                                                      @Nullable ZeebeWorkerLogger logger,
                                                      @Nullable ZeebeWorkerMetrics metrics) implements ZeebeWorkerTelemetryContext {

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
