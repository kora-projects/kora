package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientLoggerFactory {

    /**
     * @see #get(HttpClientLoggerConfig, String)
     */
    @Deprecated
    @Nullable
    default HttpClientLogger get(TelemetryConfig.LogConfig logging, String clientName) {
        return null;
    }

    @Nullable
    default HttpClientLogger get(HttpClientLoggerConfig logging, String clientName) {
        return get((TelemetryConfig.LogConfig) logging, clientName);
    }
}
