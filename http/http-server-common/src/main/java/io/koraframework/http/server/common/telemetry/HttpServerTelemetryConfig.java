package io.koraframework.http.server.common.telemetry;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigMapper
public interface HttpServerTelemetryConfig extends TelemetryConfig {

    @Override
    HttpServerLoggingConfig logging();

    @Override
    HttpServerMetricsConfig metrics();

    @Override
    HttpServerTracingConfig tracing();

    @ConfigMapper
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

    @ConfigMapper
    interface HttpServerMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigMapper
    interface HttpServerTracingConfig extends TelemetryConfig.TracingConfig {

        default boolean tracePathFull() {
            return true;
        }
    }
}
