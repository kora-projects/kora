package io.koraframework.grpc.server.telemetry.impl;

import io.koraframework.grpc.server.telemetry.GrpcServerTelemetry;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultGrpcServerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("grpc-server");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultGrpcServerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultGrpcServerMetricsFactory metricsFactory;

    public DefaultGrpcServerTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultGrpcServerLoggerFactory loggerFactory,
                                             @Nullable DefaultGrpcServerMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    public GrpcServerTelemetry get(GrpcServerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopGrpcServerTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultGrpcServerMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultGrpcServerMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopGrpcServerMetricsFactory.INSTANCE;
        }

        final DefaultGrpcServerLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultGrpcServerLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopGrpcServerLoggerFactory.INSTANCE;
        }

        return new DefaultGrpcServerTelemetry(config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }
}
