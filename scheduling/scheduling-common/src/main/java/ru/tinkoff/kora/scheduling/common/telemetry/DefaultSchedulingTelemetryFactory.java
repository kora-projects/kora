package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultSchedulingTelemetryFactory implements SchedulingTelemetryFactory {
    private final TelemetryConfig config;
    @Nullable
    private final SchedulingMetricsFactory metrics;
    @Nullable
    private final SchedulingTracerFactory tracer;
    @Nullable
    private final SchedulingLoggerFactory logger;

    public DefaultSchedulingTelemetryFactory(TelemetryConfig config, @Nullable SchedulingMetricsFactory metrics, @Nullable SchedulingTracerFactory tracer, @Nullable SchedulingLoggerFactory logger) {
        this.config = config;
        this.metrics = metrics;
        this.tracer = tracer;
        this.logger = logger;
    }

    @Override
    public SchedulingTelemetry get(@Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod) {
        var config = new SchedulingTelemetryConfig(this.config, jobTelemetryConfig);
        var metrics = this.metrics == null ? null : this.metrics.get(config.metrics(), jobClass, jobMethod);
        var tracer = this.tracer == null ? null : this.tracer.get(config.tracing(), jobClass, jobMethod);
        var logger = this.logger == null ? null : this.logger.get(config.logging(), jobClass, jobMethod);
        return new DefaultSchedulingTelemetry(jobClass, jobMethod, metrics, tracer, logger);
    }
}
