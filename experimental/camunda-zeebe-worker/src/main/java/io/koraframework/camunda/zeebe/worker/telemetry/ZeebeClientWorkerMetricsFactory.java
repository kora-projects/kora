package io.koraframework.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import org.jspecify.annotations.Nullable;
import io.koraframework.telemetry.common.TelemetryConfig;

public interface ZeebeClientWorkerMetricsFactory {

    @Nullable
    JobWorkerMetrics get(String jobType, TelemetryConfig.MetricsConfig config);
}
