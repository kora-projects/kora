package ru.tinkoff.kora.scheduling.common.telemetry;

import javax.annotation.Nullable;

public final class DefaultSchedulingTelemetryFactory implements SchedulingTelemetryFactory {
    @Nullable
    private final SchedulingMetricsFactory metrics;
    @Nullable
    private final SchedulingTracerFactory tracer;
    @Nullable
    private final SchedulingLoggerFactory logger;

    public DefaultSchedulingTelemetryFactory(@Nullable SchedulingMetricsFactory metrics, @Nullable SchedulingTracerFactory tracer, @Nullable SchedulingLoggerFactory logger) {
        this.metrics = metrics;
        this.tracer = tracer;
        this.logger = logger;
    }

    @Override
    public SchedulingTelemetry get(Class<?> jobClass, String jobMethod) {
        var metrics = this.metrics == null ? null : this.metrics.get(jobClass, jobMethod);
        var tracer = this.tracer == null ? null : this.tracer.get(jobClass, jobMethod);
        var logger = this.logger == null ? null : this.logger.get(jobClass, jobMethod);
        return new DefaultSchedulingTelemetry(metrics, tracer, logger);
    }
}
