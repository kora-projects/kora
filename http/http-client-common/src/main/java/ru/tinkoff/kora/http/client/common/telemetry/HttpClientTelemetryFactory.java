package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpClientTelemetryFactory {

    /**
     * @see #get(HttpClientTelemetryConfig, String)
     */
    @Deprecated
    @Nullable
    default HttpClientTelemetry get(TelemetryConfig config, String clientName) {
        return null;
    }

    @Nullable
    default HttpClientTelemetry get(HttpClientTelemetryConfig config, String clientName) {
        return get((TelemetryConfig) config, clientName);
    }
}
