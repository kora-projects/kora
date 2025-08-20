package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpServerTracerFactory {

    /**
     * @see #get(TelemetryConfig.TracingConfig, HttpServerConfig)
     */
    @Deprecated
    @Nullable
    default HttpServerTracer get(TelemetryConfig.TracingConfig tracing) {
        return null;
    }

    @Nullable
    default HttpServerTracer get(TelemetryConfig.TracingConfig tracingConfig, HttpServerConfig serverConfig) {
        return get(tracingConfig);
    }
}
