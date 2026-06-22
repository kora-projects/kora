package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetry;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryConfig;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultCamundaRestTelemetryFactory implements CamundaRestTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("camunda-rest");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultCamundaRestLoggerFactory loggerFactory;
    @Nullable
    private final DefaultCamundaRestMetricsFactory metricsFactory;

    public DefaultCamundaRestTelemetryFactory(@Nullable Tracer tracer,
                                              @Nullable MeterRegistry meterRegistry,
                                              @Nullable DefaultCamundaRestLoggerFactory loggerFactory,
                                              @Nullable DefaultCamundaRestMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CamundaRestTelemetry get(CamundaRestTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopCamundaRestTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultCamundaRestMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultCamundaRestMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopCamundaRestMetricsFactory.INSTANCE;
        }

        final DefaultCamundaRestLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultCamundaRestLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopCamundaRestLoggerFactory.INSTANCE;
        }

        return build(config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected CamundaRestTelemetry build(CamundaRestTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultCamundaRestMetricsFactory metricsFactory,
                                         DefaultCamundaRestLoggerFactory loggerFactory) {
        return new DefaultCamundaRestTelemetry(config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
