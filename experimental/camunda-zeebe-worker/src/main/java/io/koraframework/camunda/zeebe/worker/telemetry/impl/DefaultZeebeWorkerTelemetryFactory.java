package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryConfig;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultZeebeWorkerTelemetryFactory implements ZeebeWorkerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("zeebe-worker");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final DefaultZeebeWorkerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultZeebeWorkerMetricsFactory metricsFactory;

    public DefaultZeebeWorkerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                              @Nullable Tracer tracer,
                                              @Nullable DefaultZeebeWorkerLoggerFactory loggerFactory,
                                              @Nullable DefaultZeebeWorkerMetricsFactory metricsFactory) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public ZeebeWorkerTelemetry get(ZeebeWorkerTelemetryConfig config, String workerType) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopZeebeWorkerTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultZeebeWorkerMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultZeebeWorkerMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopZeebeWorkerMetricsFactory.INSTANCE;
        }

        final DefaultZeebeWorkerLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultZeebeWorkerLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopZeebeWorkerLoggerFactory.INSTANCE;
        }

        return build(config, workerType, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected ZeebeWorkerTelemetry build(ZeebeWorkerTelemetryConfig config,
                                         String workerType,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultZeebeWorkerMetricsFactory metricsFactory,
                                         DefaultZeebeWorkerLoggerFactory loggerFactory) {
        return new DefaultZeebeWorkerTelemetry(config, workerType, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
