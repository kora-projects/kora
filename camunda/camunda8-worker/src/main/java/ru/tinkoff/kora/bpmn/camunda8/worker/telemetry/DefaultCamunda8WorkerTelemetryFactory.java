package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda8.worker.JobContext;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultCamunda8WorkerTelemetryFactory implements Camunda8WorkerTelemetryFactory {

    private static final Camunda8WorkerTelemetry EMPTY = new NoopCamunda8WorkerTelemetry();

    @Nullable
    private final Camunda8WorkerLoggerFactory loggerFactory;
    @Nullable
    private final Camunda8WorkerMetricsFactory metricsFactory;

    public DefaultCamunda8WorkerTelemetryFactory(@Nullable Camunda8WorkerLoggerFactory loggerFactory,
                                                 @Nullable Camunda8WorkerMetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public Camunda8WorkerTelemetry get(String workerType, TelemetryConfig config) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging());
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics());
        if (metrics == null && (logger == null || Boolean.FALSE.equals(config.logging().enabled()))) {
            return EMPTY;
        }

        return new DefaultCamunda8WorkerTelemetry(workerType, logger, metrics);
    }

    private static final class NoopCamunda8WorkerTelemetry implements Camunda8WorkerTelemetry {

        private static final class NoopCamunda8WorkerTelemetryContext implements Camunda8WorkerTelemetryContext {

            @Override
            public void close() {
                // do nothing
            }

            @Override
            public void close(ErrorType errorType, Throwable throwable) {
                // do nothing
            }
        }

        private static final Camunda8WorkerTelemetryContext EMPTY = new NoopCamunda8WorkerTelemetryContext();

        @Override
        public Camunda8WorkerTelemetryContext get(JobContext jobContext) {
            return EMPTY;
        }
    }
}
