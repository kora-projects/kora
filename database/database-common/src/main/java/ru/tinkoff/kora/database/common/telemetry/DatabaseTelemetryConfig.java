package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface DatabaseTelemetryConfig extends TelemetryConfig {

    @Override
    DatabaseTracingConfig tracing();
}
