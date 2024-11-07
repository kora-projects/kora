package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpClientOperationConfig {
    @Nullable
    Duration requestTimeout();

    TelemetryConfig telemetry();
}
