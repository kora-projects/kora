package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultZeebeWorkerTelemetryFactory implements ZeebeWorkerTelemetryFactory {

    private static final ZeebeWorkerTelemetry EMPTY = new NoopZeebeWorkerTelemetry();

    @Nullable
    private final ZeebeWorkerLoggerFactory loggerFactory;
    @Nullable
    private final ZeebeWorkerMetricsFactory metricsFactory;

    public DefaultZeebeWorkerTelemetryFactory(@Nullable ZeebeWorkerLoggerFactory loggerFactory,
                                              @Nullable ZeebeWorkerMetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public ZeebeWorkerTelemetry get(String workerType, TelemetryConfig config) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging());
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics());
        if (metrics == null && (logger == null || Boolean.FALSE.equals(config.logging().enabled()))) {
            return EMPTY;
        }

        return new DefaultZeebeWorkerTelemetry(workerType, logger, metrics);
    }

    private static final class NoopZeebeWorkerTelemetry implements ZeebeWorkerTelemetry {

        private static final class NoopZeebeWorkerTelemetryContext implements ZeebeWorkerTelemetryContext {

            @Override
            public void close() {
                // do nothing
            }

            @Override
            public void close(ErrorType errorType, Throwable throwable) {
                // do nothing
            }
        }

        private static final ZeebeWorkerTelemetryContext EMPTY = new NoopZeebeWorkerTelemetryContext();

        @Override
        public ZeebeWorkerTelemetryContext get(JobContext jobContext) {
            return EMPTY;
        }
    }
}
