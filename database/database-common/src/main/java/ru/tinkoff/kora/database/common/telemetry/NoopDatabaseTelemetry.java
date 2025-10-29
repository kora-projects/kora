package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import ru.tinkoff.kora.database.common.QueryContext;

public class NoopDatabaseTelemetry implements DataBaseTelemetry {
    public static final NoopDatabaseTelemetry INSTANCE = new NoopDatabaseTelemetry();

    @Override
    public MeterRegistry meterRegistry() {
        return new CompositeMeterRegistry();
    }

    @Override
    public DataBaseObservation observe(QueryContext query) {
        return new NoopDataBaseObservation();
    }
}
