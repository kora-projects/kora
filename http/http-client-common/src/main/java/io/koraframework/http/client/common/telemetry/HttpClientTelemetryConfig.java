package io.koraframework.http.client.common.telemetry;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigMapper
public interface HttpClientTelemetryConfig extends TelemetryConfig {

    @Override
    HttpClientLoggingConfig logging();

    @Override
    HttpClientMetricsConfig metrics();

    @Override
    HttpClientTracingConfig tracing();

    @ConfigMapper
    interface HttpClientLoggingConfig extends LoggingConfig {

        default Set<String> maskQueries() {
            return Collections.emptySet();
        }

        default Set<String> maskHeaders() {
            return Set.of("authorization", "set-cookie", "cookie");
        }

        default String mask() {
            return "***";
        }

        @Nullable
        Boolean pathFull();

        default Size maxRequestBodyLogSize() {
            return Size.of(2, Size.Type.MiB);
        }

        default Size maxResponseBodyLogSize() {
            return Size.of(2, Size.Type.MiB);
        }
    }

    @ConfigMapper
    interface HttpClientMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface HttpClientTracingConfig extends TelemetryConfig.TracingConfig {

        default boolean pathFull() {
            return true;
        }
    }
}
