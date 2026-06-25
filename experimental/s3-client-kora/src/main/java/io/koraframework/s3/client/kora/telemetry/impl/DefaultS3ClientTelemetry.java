package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.s3.client.kora.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

public class DefaultS3ClientTelemetry implements S3ClientTelemetry {

    public record TelemetryContext(S3ClientTelemetryConfig config,
                                   String clientConfigPath,
                                   String clientSimpleName,
                                   String clientCanonicalName,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $S3ClientTelemetryConfig_ConfigValueExtractor.S3ClientTelemetryConfig_Impl(
                new $S3ClientTelemetryConfig_S3ClientLoggingConfig_ConfigValueExtractor.S3ClientLoggingConfig_Defaults(),
                new $S3ClientTelemetryConfig_S3ClientMetricsConfig_ConfigValueExtractor.S3ClientMetricsConfig_Defaults(),
                new $S3ClientTelemetryConfig_S3ClientTracingConfig_ConfigValueExtractor.S3ClientTracingConfig_Defaults()
            ), "none", "none", "none", false, false, DefaultS3ClientTelemetryFactory.NOOP_METER_REGISTRY, DefaultS3ClientTelemetryFactory.NOOP_TRACER);
    }

    public static final String SYSTEM_CONFIG_PATH = "system.path";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultS3ClientLoggerFactory.DefaultS3ClientLogger logger;
    protected final DefaultS3ClientMetricsFactory.DefaultS3ClientMetrics metrics;

    public DefaultS3ClientTelemetry(String clientConfigPath,
                                    Class<?> clientType,
                                    S3ClientTelemetryConfig config,
                                    Tracer tracer,
                                    MeterRegistry meterRegistry,
                                    DefaultS3ClientMetricsFactory metricsFactory,
                                    DefaultS3ClientLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultS3ClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultS3ClientTelemetryFactory.NOOP_METER_REGISTRY;
        var clientCanonicalName = clientType.getCanonicalName();
        if (clientCanonicalName == null) {
            clientCanonicalName = clientType.getName();
        }

        this.context = new TelemetryContext(config, clientConfigPath, clientType.getSimpleName(), clientCanonicalName, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public S3ClientObservation observe(String operation, String bucket) {
        var span = context.isTraceEnabled()
            ? createSpan(operation, bucket).startSpan()
            : Span.getInvalid();

        logger.logStart(operation, bucket);
        return new DefaultS3ClientObservation(bucket, operation, context, logger, metrics, span);
    }

    protected SpanBuilder createSpan(String operation, String bucket) {
        var span = this.context.tracer().spanBuilder("S3." + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "s3")
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, operation)
            .setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket)
            .setAttribute(SYSTEM_CONFIG_PATH, this.context.clientConfigPath())
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.clientSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName());
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span;
    }
}
