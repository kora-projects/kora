package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetry;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryConfig;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultS3ClientTelemetryFactory implements S3ClientTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("s3-client-kora-telemetry");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultS3ClientLoggerFactory loggerFactory;
    @Nullable
    private final DefaultS3ClientMetricsFactory metricsFactory;

    public DefaultS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                           @Nullable MeterRegistry meterRegistry,
                                           @Nullable DefaultS3ClientLoggerFactory loggerFactory,
                                           @Nullable DefaultS3ClientMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public S3ClientTelemetry get(String clientConfigPath, Class<?> clientType, S3ClientTelemetryConfig config) {
        var tracerEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!tracerEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopS3ClientTelemetry.INSTANCE;
        }

        var tracer = tracerEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultS3ClientMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultS3ClientMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopS3ClientMetricsFactory.INSTANCE;
        }

        final DefaultS3ClientLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultS3ClientLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopS3ClientLoggerFactory.INSTANCE;
        }

        return build(clientConfigPath, clientType, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected S3ClientTelemetry build(String clientConfigPath,
                                      Class<?> clientType,
                                      S3ClientTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultS3ClientMetricsFactory metricsFactory,
                                      DefaultS3ClientLoggerFactory loggerFactory) {
        return new DefaultS3ClientTelemetry(clientConfigPath, clientType, config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
