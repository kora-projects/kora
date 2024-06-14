package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultZeebeWorkerTelemetryFactory implements ZeebeWorkerTelemetryFactory {

    private static final ZeebeWorkerTelemetry EMPTY = new NoopZeebeWorkerTelemetry();

    private final ZeebeWorkerLoggerFactory loggerFactory;
    private final ZeebeWorkerMetricsFactory metricsFactory;
    private final ZeebeWorkerTracerFactory tracerFactory;

    public DefaultZeebeWorkerTelemetryFactory(@Nullable ZeebeWorkerLoggerFactory loggerFactory,
                                              @Nullable ZeebeWorkerMetricsFactory metricsFactory,
                                              @Nullable ZeebeWorkerTracerFactory tracerFactory) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.tracerFactory = tracerFactory;
    }

    @Override
    public ZeebeWorkerTelemetry get(String workerType, TelemetryConfig config) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging());
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics());
        var tracer = this.tracerFactory == null ? null : this.tracerFactory.get(config.tracing());
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY;
        }

        return new DefaultZeebeWorkerTelemetry(workerType, logger, metrics, tracer);
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
