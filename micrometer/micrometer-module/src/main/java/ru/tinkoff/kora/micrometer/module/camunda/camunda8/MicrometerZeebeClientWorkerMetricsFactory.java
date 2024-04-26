package ru.tinkoff.kora.micrometer.module.camunda.camunda8;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerZeebeClientWorkerMetricsFactory implements ZeebeClientWorkerMetricsFactory {

    private final MeterRegistry registry;

    public MicrometerZeebeClientWorkerMetricsFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Nullable
    @Override
    public JobWorkerMetrics get(String jobType, TelemetryConfig.MetricsConfig config) {
        if (config.enabled() == null || Boolean.FALSE.equals(config.enabled())) {
            return null;
        }

        return new MicrometerZeebeClientWorkerMetrics(registry, jobType);
    }
}
