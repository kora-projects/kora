package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.micrometer.core.instrument.MeterRegistry;

public final class NoopDatabaseTelemetry implements DatabaseTelemetry {

    public static final NoopDatabaseTelemetry INSTANCE = new NoopDatabaseTelemetry();

    private NoopDatabaseTelemetry() {}

    @Override
    public MeterRegistry meterRegistry() {
        return DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY;
    }

    @Override
    public DatabaseObservation observe(QueryContext query) {
        return NoopDatabaseObservation.INSTANCE;
    }
}
