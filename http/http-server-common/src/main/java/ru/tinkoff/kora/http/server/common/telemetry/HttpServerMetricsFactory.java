package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpServerMetricsFactory {

    /**
     * @see #get(TelemetryConfig.MetricsConfig, HttpServerConfig)
     */
    @Deprecated
    @Nullable
    default HttpServerMetrics get(TelemetryConfig.MetricsConfig metricsConfig) {
        return null;
    }

    @Nullable
    default HttpServerMetrics get(TelemetryConfig.MetricsConfig metricsConfig, HttpServerConfig serverConfig) {
        return get(metricsConfig);
    }
}
