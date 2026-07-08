package io.koraframework.scheduling.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface SchedulingJobConfig {

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
