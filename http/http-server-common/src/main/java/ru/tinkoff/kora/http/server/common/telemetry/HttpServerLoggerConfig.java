package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface HttpServerLoggerConfig extends TelemetryConfig.LogConfig {
    default boolean stacktrace() {
        return true;
    }
}
