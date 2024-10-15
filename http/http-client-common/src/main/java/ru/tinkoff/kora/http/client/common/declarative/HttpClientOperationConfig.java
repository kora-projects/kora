package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpClientOperationConfig {
    @Nullable
    Duration requestTimeout();

    HttpClientTelemetryConfig telemetry();
}
