package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetry;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetryFactory;

public final class NoopJdbcDatabaseTelemetryFactory implements JdbcDatabaseTelemetryFactory {

    public static final NoopJdbcDatabaseTelemetryFactory INSTANCE = new NoopJdbcDatabaseTelemetryFactory();

    private NoopJdbcDatabaseTelemetryFactory() {}

    @Override
    public JdbcDatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {
        return NoopJdbcDatabaseTelemetry.INSTANCE;
    }
}
