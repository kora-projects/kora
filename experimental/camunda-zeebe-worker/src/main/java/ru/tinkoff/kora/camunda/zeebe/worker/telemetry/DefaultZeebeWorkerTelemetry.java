package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;

public final class DefaultZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

    private final String workerType;
    private final ZeebeWorkerLogger logger;
    private final ZeebeWorkerMetrics metrics;
    private final ZeebeWorkerTracer tracer;

    public DefaultZeebeWorkerTelemetry(String workerType,
                                       @Nullable ZeebeWorkerLogger logger,
                                       @Nullable ZeebeWorkerMetrics metrics,
                                       @Nullable ZeebeWorkerTracer tracer) {
        this.workerType = workerType;
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public ZeebeWorkerTelemetryContext get(JobContext jobContext) {
        var startTime = System.nanoTime();
        if (logger != null) {
            logger.logStarted(jobContext);
        }

        final ZeebeWorkerTracer.ZeebeWorkerSpan span;
        if (tracer != null) {
            span = tracer.createSpan(workerType, jobContext);
        } else {
            span = null;
        }

        return new ZeebeWorkerTelemetryContext() {
            @Override
            public void close() {
                if (logger != null) {
                    logger.logComplete(jobContext);
                }
                if (metrics != null) {
                    var end = System.nanoTime();
                    var processingTime = end - startTime;
                    metrics.recordComplete(jobContext, processingTime);
                }
                if (span != null) {
                    span.close();
                }
            }

            @Override
            public void close(ErrorType errorType, Throwable throwable) {
                if (logger != null) {
                    logger.logFailed(jobContext, errorType, throwable);
                }
                if (metrics != null) {
                    var end = System.nanoTime();
                    var processingTime = end - startTime;
                    metrics.recordFailed(jobContext, processingTime, errorType, throwable);
                }
                if (span != null) {
                    span.close(errorType, throwable);
                }
            }
        };
    }
}
