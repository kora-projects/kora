package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

public interface HttpServerTelemetryFactory {

    /**
     * @see #get(HttpServerTelemetryConfig, HttpServerConfig)
     */
    @Deprecated
    @Nullable
    default HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig) {
        return null;
    }

    @Nullable
    default HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig, HttpServerConfig serverConfig) {
        return get(telemetryConfig);
    }
}
