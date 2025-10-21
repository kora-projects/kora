package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.LoggerFactory;

public class DefaultDataBaseTelemetryFactory implements DataBaseTelemetryFactory {
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public DefaultDataBaseTelemetryFactory(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DataBaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {

        return new DefaultDataBaseTelemetry(config, name, dbType, tracer, meterRegistry, LoggerFactory.getLogger("ru.tinkoff.kora.database." + name + ".query"));
    }
}
