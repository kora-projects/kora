package io.koraframework.database.common.telemetry;

public interface DatabaseTelemetryFactory {

    DatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType);
}
