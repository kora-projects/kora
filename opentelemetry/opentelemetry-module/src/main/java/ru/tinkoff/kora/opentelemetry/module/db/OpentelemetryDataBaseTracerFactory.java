package ru.tinkoff.kora.opentelemetry.module.db;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracer;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracerFactory;

import javax.annotation.Nullable;

public final class OpentelemetryDataBaseTracerFactory implements DataBaseTracerFactory {
    private final Tracer tracer;

    public OpentelemetryDataBaseTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public DataBaseTracer get(String dbType, @Nullable String connectionString, String user) {
        return new OpentelemetryDataBaseTracer(
            this.tracer, dbType, connectionString, user
        );
    }
}
