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

        LogConfig logging();

        TracingConfig tracing();

        MetricsConfig metrics();

        @ConfigValueExtractor
        interface LogConfig {
            @Nullable
            Boolean enabled();
        }

        @ConfigValueExtractor
        interface TracingConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();
        }

        @ConfigValueExtractor
        interface MetricsConfig {
            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }
    }
}
