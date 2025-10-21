package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface JobTelemetryConfig {
    JobLogConfig logging();

    JobTracingConfig tracing();

    JobMetricsConfig metrics();

    @ConfigValueExtractor
    interface JobLogConfig {
        @Nullable
        Boolean enabled();
    }

    @ConfigValueExtractor
    interface JobTracingConfig {
        @Nullable
        Boolean enabled();

        @Nullable
        Map<String, String> attributes();
    }

    @ConfigValueExtractor
    interface JobMetricsConfig {
        @Nullable
        Boolean enabled();

        @Nullable
        Duration[] slo();

        @Nullable
        Map<String, String> tags();
    }
}
