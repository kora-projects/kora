package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingJobTelemetryConfig;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryFactory;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultSchedulingTelemetryFactory implements SchedulingTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("scheduling-telemetry");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    private final SchedulingTelemetryConfig config;
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultSchedulingLoggerFactory loggerFactory;
    @Nullable
    private final DefaultSchedulingMetricsFactory metricsFactory;

    public DefaultSchedulingTelemetryFactory(SchedulingTelemetryConfig config,
                                             @Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultSchedulingLoggerFactory loggerFactory,
                                             @Nullable DefaultSchedulingMetricsFactory metricsFactory) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public SchedulingTelemetry get(SchedulingJobConfig.JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod) {
        var config = new SchedulingJobTelemetryConfig(this.config, jobTelemetryConfig);
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopSchedulingTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultSchedulingMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultSchedulingMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopSchedulingMetricsFactory.INSTANCE;
        }

        final DefaultSchedulingLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultSchedulingLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopSchedulingLoggerFactory.INSTANCE;
        }

        return new DefaultSchedulingTelemetry(jobClass, jobMethod, config, traceEnabled, metricEnabled, tracer, meterRegistry, enabledLoggerFactory, enabledMetricsFactory);
    }
}
