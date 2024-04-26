package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;
import ru.tinkoff.kora.bpmn.camunda8.worker.JobWorkerException;

public final class DefaultCamunda8WorkerLogger implements Camunda8WorkerLogger {

    private final Logger logger = LoggerFactory.getLogger(Camunda8WorkerLogger.class);

    @Override
    public void logStarted(JobContext context) {
        logger.debug("Camunda8 JobWorker started Job with name {} and type {} and key {} and processId {} and elementId {}",
            context.jobName(), context.jobType(), context.jobKey(), context.processId(), context.elementId());
    }

    @Override
    public void logComplete(JobContext context) {
        logger.debug("Camunda8 JobWorker completed Job with name {} and type {} and key {} and processId {} and elementId {}",
            context.jobName(), context.jobType(), context.jobKey(), context.processId(), context.elementId());
    }

    @Override
    public void logFailed(JobContext context, Camunda8WorkerTelemetry.ErrorType errorType, Throwable throwable) {
        if (throwable instanceof JobWorkerException je) {
            logger.warn("Camunda8 JobWorker completed Job with name {} and type {} and key {} and processId {} and elementId {} with code {} and message {}",
                context.jobName(), context.jobType(), context.jobKey(), context.processId(), context.elementId(), je.getCode(), je.getMessage());
        } else {
            logger.warn("Camunda8 JobWorker completed Job with name {} and type {} and key {} and processId {} and elementId {} with message {}",
                context.jobName(), context.jobType(), context.jobKey(), context.processId(), context.elementId(), throwable.getMessage());
        }
    }
}
