package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DefaultDataBaseTelemetryFactory implements DataBaseTelemetryFactory {
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultDataBaseTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DataBaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {
        var tracer = Objects.requireNonNullElse(this.tracer, TracerProvider.noop().get("database"));
        var meterRegistry = Objects.requireNonNullElse(this.meterRegistry, new CompositeMeterRegistry());

        return new DefaultDataBaseTelemetry(config, name, dbType, tracer, meterRegistry, LoggerFactory.getLogger("ru.tinkoff.kora.database." + name + ".query"));
    }
}
