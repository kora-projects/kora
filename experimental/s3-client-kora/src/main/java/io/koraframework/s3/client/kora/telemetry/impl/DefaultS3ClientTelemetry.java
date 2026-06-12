package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.s3.client.kora.telemetry.S3ClientObservation;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetry;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

public class DefaultS3ClientTelemetry implements S3ClientTelemetry {

    public record TelemetryContext(S3ClientTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new S3ClientTelemetryConfig() {
            @Override
            public S3ClientLogConfig logging() {
                return new S3ClientLogConfig() {};
            }

            @Override
            public S3ClientMetricsConfig metrics() {
                return new S3ClientMetricsConfig() {};
            }

            @Override
            public S3ClientTracingConfig tracing() {
                return new S3ClientTracingConfig() {};
            }
        }, false, false, DefaultS3ClientTelemetryFactory.NOOP_METER_REGISTRY, DefaultS3ClientTelemetryFactory.NOOP_TRACER);
    }

    protected final TelemetryContext context;
    protected final DefaultS3ClientLoggerFactory.DefaultS3ClientLogger logger;
    protected final DefaultS3ClientMetricsFactory.DefaultS3ClientMetrics metrics;

    public DefaultS3ClientTelemetry(S3ClientTelemetryConfig config,
                                    Tracer tracer,
                                    MeterRegistry meterRegistry,
                                    DefaultS3ClientMetricsFactory metricsFactory,
                                    DefaultS3ClientLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultS3ClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultS3ClientTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public S3ClientObservation observe(String operation, String bucket) {
        var span = context.isTraceEnabled
            ? createSpan(operation, bucket).startSpan()
            : Span.getInvalid();

        logger.logStart(operation, bucket);
        return new DefaultS3ClientObservation(bucket, operation, context, logger, metrics, span);
    }

    protected SpanBuilder createSpan(String operation, String bucket) {
        var span = this.context.tracer().spanBuilder("S3." + operation)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "s3")
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, operation)
            .setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span;
    }
}
