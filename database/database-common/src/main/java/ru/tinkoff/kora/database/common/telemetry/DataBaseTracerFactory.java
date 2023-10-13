package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface DataBaseTracerFactory {
    @Nullable
    DataBaseTracer get(TelemetryConfig.TracingConfig tracing, String dbType, @Nullable String connectionString, String user);
}
