package io.koraframework.s3.client.aws.telemetry;

import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultAwsS3ClientTelemetry implements AwsS3ClientTelemetry {

    protected final ConcurrentMap<Tags, Timer> durationCache = new ConcurrentHashMap<>();

    protected final AwsS3ClientTelemetryConfig config;
    protected final Tracer tracer;
    protected final MeterRegistry meterRegistry;
    protected final Logger logger;
    protected final Meter.MeterProvider<Timer> requestDurationMeter;

    public DefaultAwsS3ClientTelemetry(AwsS3ClientTelemetryConfig config,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;

        this.requestDurationMeter = tags ->
            durationCache.computeIfAbsent(Tags.of(tags), _ -> createMetricClientDuration()
                .tags((Iterable<Tag>) tags)
                .register(this.meterRegistry));

        var logger = LoggerFactory.getLogger(AwsS3ClientTelemetry.class);
        this.logger = this.config.logging().enabled() && logger.isWarnEnabled()
            ? logger
            : NOPLogger.NOP_LOGGER;
    }

    @Override
    public AwsS3ClientObservation observe(String operation, String bucket) {
        var span = this.config.tracing().enabled()
            ? createSpan(operation, bucket).startSpan()
            : Span.getInvalid();

        logger.debug("S3AwsClient starting S3 operation '{}' on bucket: {}", operation, bucket);
        return new DefaultAwsS3ClientObservation(bucket, operation, config, span, logger, requestDurationMeter);
    }

    protected Timer.Builder createMetricClientDuration() {
        var staticTags = new ArrayList<Tag>(1 + this.config.metrics().tags().size());
        staticTags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "s3-aws"));
        for (var e : this.config.metrics().tags().entrySet()) {
            staticTags.add(Tag.of(e.getKey(), e.getValue()));
        }

        return Timer.builder("rpc.client.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tags(staticTags);
    }

    protected SpanBuilder createSpan(String operation, String bucket) {
        var span = this.tracer.spanBuilder("S3." + operation)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "s3")
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, operation)
            .setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span;
    }
}
