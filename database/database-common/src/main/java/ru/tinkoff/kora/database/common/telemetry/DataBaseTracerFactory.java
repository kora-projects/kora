package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;

public interface DataBaseTracerFactory {
    DataBaseTracer get(String dbType, @Nullable String connectionString, String user);
}
