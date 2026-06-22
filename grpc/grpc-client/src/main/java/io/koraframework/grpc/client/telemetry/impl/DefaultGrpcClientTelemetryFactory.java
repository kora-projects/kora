package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.ServiceDescriptor;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetry;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.net.URI;

public class DefaultGrpcClientTelemetryFactory implements GrpcClientTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("grpc-client");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultGrpcClientLoggerFactory loggerFactory;
    @Nullable
    private final DefaultGrpcClientMetricsFactory metricsFactory;

    public DefaultGrpcClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultGrpcClientLoggerFactory loggerFactory,
                                             @Nullable DefaultGrpcClientMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public GrpcClientTelemetry get(GrpcClientTelemetryConfig config, ServiceDescriptor service, URI uri) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopGrpcClientTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultGrpcClientMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultGrpcClientMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopGrpcClientMetricsFactory.INSTANCE;
        }

        final DefaultGrpcClientLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultGrpcClientLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopGrpcClientLoggerFactory.INSTANCE;
        }

        return build(config, service, uri, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected GrpcClientTelemetry build(GrpcClientTelemetryConfig config,
                                        ServiceDescriptor service,
                                        URI uri,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultGrpcClientMetricsFactory metricsFactory,
                                        DefaultGrpcClientLoggerFactory loggerFactory) {
        return new DefaultGrpcClientTelemetry(config, service, uri, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
