package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface HttpServerTelemetryConfig extends TelemetryConfig {
    /**
     * @return Logging telemetry configuration of incoming requests.
     */
    @Override
    HttpServerLoggerConfig logging();
}
