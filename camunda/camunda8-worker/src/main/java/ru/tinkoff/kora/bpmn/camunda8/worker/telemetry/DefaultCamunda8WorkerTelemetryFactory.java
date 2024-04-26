package ru.tinkoff.kora.bpmn.camunda8.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultCamunda8WorkerTelemetryFactory implements Camunda8WorkerTelemetryFactory {

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
    public Camunda8WorkerTelemetry get(TelemetryConfig config) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging());
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics());
        if (metrics == null && (logger == null || Boolean.FALSE.equals(config.logging().enabled()))) {
            return null;
        }

        return new DefaultCamunda8WorkerTelemetry(logger, metrics);
    }
}
