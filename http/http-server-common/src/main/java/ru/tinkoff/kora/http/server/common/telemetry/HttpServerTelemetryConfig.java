package ru.tinkoff.kora.http.server.common.telemetry;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
    interface HttpServerLoggingConfig extends LogConfig {
        default boolean stacktrace() {
            return true;
        }

        default Set<String> maskQueries() {
            return Set.of();
        }

        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }

        default String mask() {
            return "***";
        }

        @Nullable
        Boolean pathTemplate();
    }

    @ConfigValueExtractor
    interface HttpServerMetricsConfig extends TelemetryConfig.MetricsConfig {

    }

    @ConfigValueExtractor
    interface HttpServerTracingConfig extends TelemetryConfig.TracingConfig {
    }
}
