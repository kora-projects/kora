package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Set;

@ConfigValueExtractor
public interface HttpClientOperationConfig {
    @Nullable
    Duration requestTimeout();

    OperationTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface OperationTelemetryConfig {
        LoggingConfig logging();

        TracingConfig tracing();

        MetricsConfig metrics();

        @ConfigValueExtractor
        interface LoggingConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Set<String> maskQueries();

            @Nullable
            Set<String> maskHeaders();

            @Nullable
            String mask();

            @Nullable
            Boolean pathTemplate();
        }

        @ConfigValueExtractor
        interface TracingConfig {
            @Nullable
            Boolean enabled();
        }

        @ConfigValueExtractor
        interface MetricsConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Duration[] slo();
        }
    }
}
