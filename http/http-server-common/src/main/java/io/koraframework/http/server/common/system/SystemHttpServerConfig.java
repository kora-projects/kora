package io.koraframework.http.server.common.system;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;

@ConfigMapper
public interface SystemHttpServerConfig extends HttpServerConfig {

    default String metricsPath() {
        return "/metrics";
    }

    default String readinessPath() {
        return "/system/readiness";
    }

    default String livenessPath() {
        return "/system/liveness";
    }

    @Override
    SystemHttpServerTelemetryConfig telemetry();

    @ConfigMapper
    interface SystemHttpServerTelemetryConfig extends HttpServerTelemetryConfig {

        @Override
        SystemHttpServerLoggingConfig logging();

        @Override
        SystemHttpServerMetricsConfig metrics();

        @Override
        SystemHttpServerTracingConfig tracing();

        @ConfigMapper
        interface SystemHttpServerLoggingConfig extends HttpServerLoggingConfig { }

        @ConfigMapper
        interface SystemHttpServerMetricsConfig extends HttpServerMetricsConfig { }

        @ConfigMapper
        interface SystemHttpServerTracingConfig extends HttpServerTracingConfig {

            @Override
            default boolean enabled() {
                return false;
            }
        }
    }
}
