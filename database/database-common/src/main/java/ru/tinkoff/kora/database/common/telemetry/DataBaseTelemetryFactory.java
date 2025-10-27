package ru.tinkoff.kora.database.common.telemetry;

public interface DataBaseTelemetryFactory {
    DataBaseTelemetryFactory NOOP = (_, _, _) -> NoopDatabaseTelemetry.INSTANCE;

    DataBaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType);
}
