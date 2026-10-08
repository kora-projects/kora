package io.koraframework.database.jdbc.telemetry;

import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;

public interface JdbcDatabaseTelemetryFactory {

    JdbcDatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType);
}
