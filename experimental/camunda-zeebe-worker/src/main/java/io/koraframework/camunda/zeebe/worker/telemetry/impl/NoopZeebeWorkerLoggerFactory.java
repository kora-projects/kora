package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.client.api.response.ActivatedJob;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopZeebeWorkerLoggerFactory extends DefaultZeebeWorkerLoggerFactory {

    public static final NoopZeebeWorkerLoggerFactory INSTANCE = new NoopZeebeWorkerLoggerFactory();

    private NoopZeebeWorkerLoggerFactory() {}

    @Override
    public DefaultZeebeWorkerLogger create(DefaultZeebeWorkerTelemetry.TelemetryContext context) {
        return NoopZeebeWorkerLogger.INSTANCE;
    }

    public static final class NoopZeebeWorkerLogger extends DefaultZeebeWorkerLogger {

        public static final NoopZeebeWorkerLogger INSTANCE = new NoopZeebeWorkerLogger();

        private NoopZeebeWorkerLogger() {
            super(NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logJobHandle(ActivatedJob job) {

        }

        @Override
        public void logJobEnd(ActivatedJob job, @Nullable Throwable error, boolean failedByUser, long processingTimeNanos) {

        }
    }
}
