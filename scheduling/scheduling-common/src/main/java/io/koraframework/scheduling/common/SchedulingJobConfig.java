package io.koraframework.scheduling.common;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface SchedulingJobConfig {

    JobTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface JobTelemetryConfig {

        JobLoggingConfig logging();

        JobMetricsConfig metrics();

        JobTracingConfig tracing();

        @ConfigValueExtractor
        interface JobLoggingConfig {
            @Nullable
            Boolean enabled();
        }

        @ConfigValueExtractor
        interface JobMetricsConfig {
            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }

        @ConfigValueExtractor
        interface JobTracingConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();
        }
    }
}
