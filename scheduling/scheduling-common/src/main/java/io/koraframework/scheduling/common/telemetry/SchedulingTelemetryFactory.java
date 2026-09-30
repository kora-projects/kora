package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig;
import org.jspecify.annotations.Nullable;

public interface SchedulingTelemetryFactory {

    /**
     * @param schedulerType      scheduler that runs the job, reported as the {@code scheduling.system} metric tag and span
     *                           attribute: {@code jdk}, {@code quartz} or {@code dbscheduler}
     * @param jobConfigPath      configuration path of the job, {@code null} for a job declared without configuration
     * @param jobTelemetryConfig telemetry configuration of the job, {@code null} for a job declared without configuration
     * @param jobClass           class declaring the job method
     * @param jobMethod          job method
     * @return telemetry of the job
     */
    SchedulingTelemetry get(String schedulerType, @Nullable String jobConfigPath, @Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
