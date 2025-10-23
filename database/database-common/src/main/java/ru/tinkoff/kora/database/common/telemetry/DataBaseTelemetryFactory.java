package ru.tinkoff.kora.database.common.telemetry;

public interface DataBaseTelemetryFactory {
    DataBaseTelemetryFactory NOOP = (_, _, _) -> _ -> new NoopDataBaseObservation();

    DataBaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType);
}
