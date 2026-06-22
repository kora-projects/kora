package io.koraframework.http.server.common.telemetry;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpServerTelemetryConfig extends TelemetryConfig {

    @Override
    HttpServerLoggingConfig logging();

    @Override
    HttpServerMetricsConfig metrics();

    @Override
    HttpServerTracingConfig tracing();

    @ConfigValueExtractor
    interface HttpServerLoggingConfig extends LoggingConfig {

        default boolean stacktrace() {
            return true;
        }

        default Set<String> maskQueries() {
            return Collections.emptySet();
        }

        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
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

    @ConfigValueExtractor
    interface HttpServerMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigValueExtractor
    interface HttpServerTracingConfig extends TelemetryConfig.TracingConfig {

        default boolean tracePathFull() {
            return true;
        }
    }
}
