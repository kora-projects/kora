package io.koraframework.http.client.common.declarative;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@ConfigMapper
public interface HttpClientOperationConfig {

    /**
     * @return Maximum request time that may include DNS resolution, connection, request body write, server processing and response body read.
     */
    @Nullable
    Duration requestTimeout();

    /**
     * @return Telemetry settings that override the client telemetry settings for this operation.
     */
    OperationTelemetryConfig telemetry();

    @ConfigMapper
    interface OperationTelemetryConfig {

        LoggingConfig logging();

        TracingConfig tracing();

        MetricsConfig metrics();

        @ConfigMapper
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
            Boolean pathFull();

            @Nullable
            Size maxRequestBodyLogSize();

            @Nullable
            Size maxResponseBodyLogSize();
        }

        @ConfigMapper
        interface TracingConfig {
            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();

            @Nullable
            Boolean pathFull();
        }

        @ConfigMapper
        interface MetricsConfig {
            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }
    }
}
