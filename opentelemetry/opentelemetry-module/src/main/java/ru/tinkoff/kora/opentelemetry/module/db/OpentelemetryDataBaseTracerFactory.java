package ru.tinkoff.kora.opentelemetry.module.db;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracer;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryDataBaseTracerFactory implements DataBaseTracerFactory {
    private final Tracer tracer;

    public OpentelemetryDataBaseTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    @Nullable
    public DataBaseTracer get(TelemetryConfig.TracingConfig config, String dbType, @Nullable String connectionString, String user) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryDataBaseTracer(this.tracer, dbType, connectionString, user);
        } else {
            return null;
        }
    }
}
