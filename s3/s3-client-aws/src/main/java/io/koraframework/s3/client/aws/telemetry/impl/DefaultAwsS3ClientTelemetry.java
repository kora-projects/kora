package io.koraframework.s3.client.aws.telemetry.impl;

import io.koraframework.s3.client.aws.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

public class DefaultAwsS3ClientTelemetry implements AwsS3ClientTelemetry {

    public record TelemetryContext(AwsS3ClientTelemetryConfig config,
                                   String clientConfigPath,
                                   String clientSimpleName,
                                   String clientCanonicalName,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $AwsS3ClientTelemetryConfig_ConfigValueExtractor.AwsS3ClientTelemetryConfig_Impl(
                new $AwsS3ClientTelemetryConfig_S3ClientLogConfig_ConfigValueExtractor.S3LoggingConfig_Defaults(),
                new $AwsS3ClientTelemetryConfig_S3ClientMetricsConfig_ConfigValueExtractor.S3MetricsConfig_Defaults(),
                new $AwsS3ClientTelemetryConfig_S3ClientTracingConfig_ConfigValueExtractor.S3TracingConfig_Defaults()
            ), "none", "none", "none", false, false, DefaultAwsS3ClientTelemetryFactory.NOOP_METER_REGISTRY, DefaultAwsS3ClientTelemetryFactory.NOOP_TRACER);
    }

    public static final String SYSTEM_CONFIG_PATH = "system.path";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultAwsS3ClientLoggerFactory.DefaultAwsS3ClientLogger logger;
    protected final DefaultAwsS3ClientMetricsFactory.DefaultAwsS3ClientMetrics metrics;

    public DefaultAwsS3ClientTelemetry(String clientConfigPath,
                                       Class<?> clientType,
                                       AwsS3ClientTelemetryConfig config,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultAwsS3ClientMetricsFactory metricsFactory,
                                       DefaultAwsS3ClientLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultAwsS3ClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultAwsS3ClientTelemetryFactory.NOOP_METER_REGISTRY;
        var clientCanonicalName = clientType.getCanonicalName();
        if (clientCanonicalName == null) {
            clientCanonicalName = clientType.getName();
        }

        this.context = new TelemetryContext(config, clientConfigPath, clientType.getSimpleName(), clientCanonicalName, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public AwsS3ClientObservation observe(String operation, String bucket) {
        var span = context.isTraceEnabled()
            ? createSpan(operation, bucket).startSpan()
            : Span.getInvalid();

        logger.logStart(operation, bucket);
        return new DefaultAwsS3ClientObservation(bucket, operation, context, logger, metrics, span);
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
