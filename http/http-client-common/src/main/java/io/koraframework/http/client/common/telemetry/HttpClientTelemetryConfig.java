package io.koraframework.http.client.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface HttpClientTelemetryConfig extends TelemetryConfig {
    @Override
    HttpClientLoggerConfig logging();

    @Override
    HttpClientTracingConfig tracing();

    @Override
    HttpClientMetricsConfig metrics();

    @ConfigValueExtractor
    interface HttpClientLoggerConfig extends TelemetryConfig.LogConfig {

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
        Boolean pathTemplate();
    }

    @ConfigValueExtractor
    interface HttpClientTracingConfig extends TelemetryConfig.TracingConfig {
    }

    @ConfigValueExtractor
    interface HttpClientMetricsConfig extends TelemetryConfig.MetricsConfig {
    }

}
