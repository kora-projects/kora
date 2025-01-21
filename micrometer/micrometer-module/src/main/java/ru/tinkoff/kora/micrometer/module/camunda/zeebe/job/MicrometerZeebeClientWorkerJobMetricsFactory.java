package ru.tinkoff.kora.micrometer.module.camunda.zeebe.job;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerZeebeClientWorkerJobMetricsFactory implements ZeebeClientWorkerMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerZeebeClientWorkerJobMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public JobWorkerMetrics get(String jobType, TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new MicrometerZeebeClientWorkerJobMetrics(this.meterRegistry, jobType);
        } else {
            return null;
        }
    }
}
