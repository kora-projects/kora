package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.JobWorkerException;

public final class DefaultZeebeWorkerLogger implements ZeebeWorkerLogger {

    private final Logger logger = LoggerFactory.getLogger(ZeebeWorkerLogger.class);

    @Override
    public void logStarted(JobContext context) {
        logger.debug("Zeebe JobWorker started Job {}", context);
    }

    @Override
    public void logComplete(JobContext context) {
        logger.debug("Zeebe JobWorker completed Job {}", context);
    }

    @Override
    public void logFailed(JobContext context, ZeebeWorkerTelemetry.ErrorType errorType, Throwable throwable) {
        if (throwable instanceof JobWorkerException je) {
            logger.warn("Zeebe JobWorker failed Job {} with code {} and message {}", context, je.getCode(), je.getMessage());
        } else {
            logger.warn("Zeebe JobWorker failed Job {} with message {}", context, throwable.getMessage());
        }
    }
}
