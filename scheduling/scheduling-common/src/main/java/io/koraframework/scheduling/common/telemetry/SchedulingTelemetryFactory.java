package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.SchedulingJobConfig;
import org.jspecify.annotations.Nullable;

public interface SchedulingTelemetryFactory {

    SchedulingTelemetry get(SchedulingJobConfig.JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
