package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;

public final class NoopDatabaseTelemetryFactory implements DatabaseTelemetryFactory {

    public static final NoopDatabaseTelemetryFactory INSTANCE = new NoopDatabaseTelemetryFactory();

    private NoopDatabaseTelemetryFactory() {}

    @Override
    public DatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {
        return NoopDatabaseTelemetry.INSTANCE;
    }
}
