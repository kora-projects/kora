package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
            return Set.of("authorization", "cookie");
        }

        default String mask() {
            return "***";
        }

        default boolean pathTemplate() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface HttpClientTracingConfig extends TelemetryConfig.TracingConfig {
    }

    @ConfigValueExtractor
    interface HttpClientMetricsConfig extends TelemetryConfig.MetricsConfig {
    }

}
