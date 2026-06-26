package io.koraframework.s3.client.aws.telemetry.impl;

import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetry;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryConfig;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultAwsS3ClientTelemetryFactory implements AwsS3ClientTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("s3-client-aws-telemetry");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultAwsS3ClientLoggerFactory loggerFactory;
    @Nullable
    private final DefaultAwsS3ClientMetricsFactory metricsFactory;

    public DefaultAwsS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                              @Nullable MeterRegistry meterRegistry,
                                              @Nullable DefaultAwsS3ClientLoggerFactory loggerFactory,
                                              @Nullable DefaultAwsS3ClientMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public AwsS3ClientTelemetry get(String clientConfigPath, Class<?> clientType, AwsS3ClientTelemetryConfig config) {
        var tracerEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!tracerEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopAwsS3ClientTelemetry.INSTANCE;
        }

        var tracer = tracerEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultAwsS3ClientMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultAwsS3ClientMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopAwsS3ClientMetricsFactory.INSTANCE;
        }

        final DefaultAwsS3ClientLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultAwsS3ClientLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopAwsS3ClientLoggerFactory.INSTANCE;
        }

        return build(clientConfigPath, clientType, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected AwsS3ClientTelemetry build(String clientConfigPath,
                                         Class<?> clientType,
                                         AwsS3ClientTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultAwsS3ClientMetricsFactory metricsFactory,
                                         DefaultAwsS3ClientLoggerFactory loggerFactory) {
        return new DefaultAwsS3ClientTelemetry(clientConfigPath, clientType, config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
