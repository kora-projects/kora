package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpClientOperationConfig {
    /**
     * @return Maximum request time that may include DNS resolution, connection, request body write, server processing and response body read.
     */
    @Nullable
    Duration requestTimeout();

    /**
     * @return Telemetry settings that override the client telemetry settings for this operation.
     */
    TelemetryConfig telemetry();
}
