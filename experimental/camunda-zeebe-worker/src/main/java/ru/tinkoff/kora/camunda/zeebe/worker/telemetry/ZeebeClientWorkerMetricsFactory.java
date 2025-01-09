package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface ZeebeClientWorkerMetricsFactory {

    @Nullable
    JobWorkerMetrics get(String jobType, TelemetryConfig.MetricsConfig config);
}
