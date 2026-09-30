package io.koraframework.scheduling.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration of a scheduled job declared with the {@code config} attribute of a scheduling annotation.
 */
@ConfigMapper
public interface SchedulingJobConfig {

    /**
     * Whether the job is scheduled.
     *
     * <p>A disabled job is never executed. Jobs of persistent schedulers are unscheduled as well: a Quartz job
     * loses its triggers and a database scheduled job loses its scheduled execution on application start.
     *
     * @return {@code false} to disable the job
     */
    default boolean enabled() {
        return true;
    }

    JobTelemetryConfig telemetry();

    @ConfigMapper
    interface JobTelemetryConfig {

        JobLoggingConfig logging();

        JobMetricsConfig metrics();

        JobTracingConfig tracing();

        @ConfigMapper
        interface JobLoggingConfig {
            @Nullable
            Boolean enabled();
        }

        @ConfigMapper
        interface JobMetricsConfig {
            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }

        @ConfigMapper
        interface JobTracingConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();
        }
    }
}
