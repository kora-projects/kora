package ru.tinkoff.kora.scheduling.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.scheduling.common.telemetry.DefaultSchedulingTelemetryFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingLoggerFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracerFactory;

import javax.annotation.Nullable;

public interface SchedulingModule {
    @DefaultComponent
    default DefaultSchedulingTelemetryFactory defaultSchedulingTelemetryFactory(@Nullable SchedulingMetricsFactory metrics, @Nullable SchedulingTracerFactory tracer, @Nullable SchedulingLoggerFactory logger) {
        return new DefaultSchedulingTelemetryFactory(metrics, tracer, logger);
    }
}
