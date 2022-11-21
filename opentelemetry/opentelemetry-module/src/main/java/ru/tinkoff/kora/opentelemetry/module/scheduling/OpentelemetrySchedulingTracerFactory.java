package ru.tinkoff.kora.opentelemetry.module.scheduling;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracer;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracerFactory;

public class OpentelemetrySchedulingTracerFactory implements SchedulingTracerFactory {
    private final Tracer tracer;

    public OpentelemetrySchedulingTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public SchedulingTracer get(Class<?> jobClass, String jobMethod) {
        return new OpentelemetrySchedulingTracer(this.tracer, jobClass.getCanonicalName(), jobMethod);
    }
}
