package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig;
import org.jspecify.annotations.Nullable;

public interface SchedulingTelemetryFactory {

    SchedulingTelemetry get(@Nullable String jobConfigPath, @Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
