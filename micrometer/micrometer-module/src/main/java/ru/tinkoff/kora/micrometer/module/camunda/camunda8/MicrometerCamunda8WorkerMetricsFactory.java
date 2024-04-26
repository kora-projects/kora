package ru.tinkoff.kora.micrometer.module.camunda.camunda8;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerMetrics;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerCamunda8WorkerMetricsFactory implements Camunda8WorkerMetricsFactory {

    private final MeterRegistry registry;

    public MicrometerCamunda8WorkerMetricsFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Nullable
    @Override
    public Camunda8WorkerMetrics get(TelemetryConfig.MetricsConfig config) {
        if (config.enabled() == null || Boolean.FALSE.equals(config.enabled())) {
            return null;
        }

        return new MicrometerCamunda8WorkerMetrics(registry, config);
    }
}
