package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface DatabaseTracingConfig extends TelemetryConfig.TracingConfig {

    default boolean traceConnectionURI() {
        return false;
    }
}
