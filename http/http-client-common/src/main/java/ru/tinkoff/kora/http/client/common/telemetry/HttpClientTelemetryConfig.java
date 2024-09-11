package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface HttpClientTelemetryConfig extends TelemetryConfig {
    @Override
    HttpClientLoggerConfig logging();
}
