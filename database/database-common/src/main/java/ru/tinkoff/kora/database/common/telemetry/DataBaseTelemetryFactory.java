package ru.tinkoff.kora.database.common.telemetry;

public interface DataBaseTelemetryFactory {
    DataBaseTelemetry get(String name, String driverType, String dbType, String username);
}
