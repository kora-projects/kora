package io.koraframework.openfeature.telemetry.impl;

import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetryConfig;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultOpenfeatureTelemetryFactory implements OpenfeatureTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("openfeature");
    public static final MeterRegistry NOOP_METER_REGISTRY = NoopMeterRegistry.INSTANCE;

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultOpenfeatureLoggerFactory loggerFactory;
    @Nullable
    private final DefaultOpenfeatureMetricsFactory metricsFactory;

    public DefaultOpenfeatureTelemetryFactory(@Nullable Tracer tracer,
                                              @Nullable MeterRegistry meterRegistry,
                                              @Nullable DefaultOpenfeatureLoggerFactory loggerFactory,
                                              @Nullable DefaultOpenfeatureMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public OpenfeatureTelemetry get(String clientConfigPath, String clientCanonicalName, OpenfeatureTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopOpenfeatureTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        var enabledMetricsFactory = metricEnabled
            ? (this.metricsFactory != null ? this.metricsFactory : DefaultOpenfeatureMetricsFactory.INSTANCE)
            : NoopOpenfeatureMetricsFactory.INSTANCE;
        var enabledLoggerFactory = config.logging().enabled()
            ? (this.loggerFactory != null ? this.loggerFactory : DefaultOpenfeatureLoggerFactory.INSTANCE)
            : NoopOpenfeatureLoggerFactory.INSTANCE;

        return build(clientConfigPath, clientCanonicalName, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected OpenfeatureTelemetry build(String clientConfigPath,
                                         String clientCanonicalName,
                                         OpenfeatureTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultOpenfeatureMetricsFactory metricsFactory,
                                         DefaultOpenfeatureLoggerFactory loggerFactory) {
        return new DefaultOpenfeatureTelemetry(clientConfigPath, clientCanonicalName, config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
