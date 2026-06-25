package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.telemetry.SoapClientTelemetry;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import io.koraframework.soap.client.common.SoapMethodDescriptor;

public class DefaultSoapClientTelemetryFactory implements SoapClientTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("soap-client");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultSoapClientLoggerFactory loggerFactory;
    @Nullable
    private final DefaultSoapClientMetricsFactory metricsFactory;

    public DefaultSoapClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultSoapClientLoggerFactory loggerFactory,
                                             @Nullable DefaultSoapClientMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public SoapClientTelemetry get(String clientConfigPath,
                                   String clientCanonicalName,
                                   SoapClientTelemetryConfig config,
                                   SoapMethodDescriptor descriptor,
                                   String url) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopSoapClientTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultSoapClientMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultSoapClientMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopSoapClientMetricsFactory.INSTANCE;
        }

        final DefaultSoapClientLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultSoapClientLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopSoapClientLoggerFactory.INSTANCE;
        }

        return build(clientConfigPath, clientCanonicalName, config, descriptor, url, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected SoapClientTelemetry build(String clientConfigPath,
                                        String clientCanonicalName,
                                        SoapClientTelemetryConfig config,
                                        SoapMethodDescriptor descriptor,
                                        String url,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultSoapClientMetricsFactory metricsFactory,
                                        DefaultSoapClientLoggerFactory loggerFactory) {
        return new DefaultSoapClientTelemetry(clientConfigPath, clientCanonicalName, config, descriptor, url, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
